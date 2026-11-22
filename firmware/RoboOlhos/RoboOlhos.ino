#include <Arduino.h>
#include <Wire.h>
#include <WiFi.h>
#include <WebServer.h>
#include <Preferences.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include "robot.h"
#include "web_ui.h"
#include "ble_transport.h"

Adafruit_SSD1306 display(128,64,&Wire,-1);
WebServer server(80);
Preferences prefs;
Robot robot;
BootButton button;
Lesson lessons[MAX_LESSONS] = {};
uint8_t lessonCount=0;
bool memoryReady=false, oledReady=false, lively=false;
String userName, interest, apName, apPassword, lastText;
uint32_t frameAt=0, blinkAt=0, blinkStart=0, gazeAt=0;
bool blinking=false;
float gazeX=0, gazeTarget=0, eyeHeight=32, brow=0, smile=0, asymmetry=0;
char serialLine[241] = {};
size_t serialLength=0;
bool serialOverflow=false;
unsigned int replies[4] = {};
int savedMood=-1, savedIntensity=-1;

String jsonString(const String& value) {
  String out="\"";
  for (size_t i=0;i<value.length();++i) {
    uint8_t c=value[i];
    if(c=='"' || c=='\\') {out+='\\';out+=(char)c;}
    else if(c=='\n')out+="\\n";
    else if(c=='\r')out+="\\r";
    else if(c=='\t')out+="\\t";
    else if(c<32) {char b[7];snprintf(b,sizeof(b),"\\u%04x",c);out+=b;}
    else out+=(char)c;
  }
  return out+'"';
}
void saveMood() {
  if (savedMood==(int)robot.mood && savedIntensity==robot.intensity) return;
  savedMood=robot.mood;savedIntensity=robot.intensity;
  if(memoryReady) {prefs.putUChar("mood",robot.mood);prefs.putUChar("strength",robot.intensity);}
}
void saveProfile() {
  if(memoryReady) {prefs.putString("name",userName);prefs.putString("interest",interest);prefs.putBool("lively",lively);}
}
void saveLessons() {
  if(memoryReady) {
    if(lessonCount) prefs.putBytes("lessons",lessons,sizeof(Lesson)*lessonCount);
    else prefs.remove("lessons");
  }
}
String stateJson() {
  return "{\"face\":"+jsonString(FACE_NAMES[robot.face()])+",\"mood\":"+jsonString(FACE_NAMES[robot.mood])+",\"name\":"+jsonString(userName)+",\"interest\":"+jsonString(interest)+",\"pace\":"+jsonString(lively?"animado":"calmo")+",\"demo\":"+(robot.demo?"true":"false")+",\"memory\":"+(memoryReady?"true":"false")+",\"lessons\":"+String(lessonCount)+"}";
}
int labelIndex(const String& s) {
  for(int i=0;i<4;++i) if(s==NOMES[i]) return i;
  return -1;
}
bool validField(const String& s,size_t limit) {
  if(s.length()>limit)return false;
  for(size_t i=0;i<s.length();++i)if((uint8_t)s[i]<32)return false;
  return true;
}
String teachText(const String& text,int label) {
  if(text.length()>240 || text.isEmpty() || label<0 || label>3) return "Use uma frase de ate 240 bytes e feliz, triste, raiva ou neutro.";
  if(!learn(lessons,lessonCount,text.c_str(),label))return "Essa frase nao tem palavras para aprender.";
  saveLessons();robot.setMood((Face)label,millis());saveMood();
  return "Aprendi a classificacao dessa frase. Variacoes com palavras diferentes precisam de novos exemplos.";
}
String respond(const String& original) {
  String text=original;text.trim();
  if(text.isEmpty())return "Digite uma frase.";
  if(text.length()>240)return "Limite: 240 bytes por mensagem.";
  uint32_t now=millis();robot.wake(now);robot.demo=false;
  if(text=="/ajuda")return "Frases: estou feliz / estou triste / estou com raiva. Comandos: /demo /neutro /surpresa /curioso /dormir /nome Ana /gosto musica /ritmo calmo /ritmo animado /memoria /corrigir feliz|triste|raiva|neutro /apagar /wifi";
  if(text=="/wifi")return "Rede: "+apName+" | Senha: "+apPassword+" | Abra http://192.168.4.1";
  if(text=="/demo") {robot.demo=true;robot.demoFace=0;robot.lastDemo=now;return "Demonstracao ligada. Envie uma frase para sair.";}
  if(text=="/neutro") {robot.setMood(NEUTRAL,now);saveMood();return "De volta ao normal.";}
  if(text=="/surpresa" || text=="/curioso") {robot.pose(text=="/surpresa"?SURPRISE:CURIOUS,now);return "Olha minha expressao!";}
  if(text=="/dormir") {robot.lastActivity=now-SLEEP_AFTER;robot.sleeping=true;return "Vou descansar. Uma mensagem ou toque em BOOT me acorda.";}
  if(text.startsWith("/nome ")) {String v=text.substring(6);v.trim();if(!validField(v,24))return "Nome: ate 24 bytes, sem caracteres de controle.";userName=v;saveProfile();return "Combinado, "+userName+"!";}
  if(text.startsWith("/gosto ")) {String v=text.substring(7);v.trim();if(!validField(v,40))return "Preferencia: ate 40 bytes, sem caracteres de controle.";interest=v;saveProfile();return "Vou lembrar que voce gosta de "+interest+".";}
  if(text.startsWith("/ritmo ")) {String v=text.substring(7);if(v!="calmo" && v!="animado")return "Use /ritmo calmo ou /ritmo animado.";lively=v=="animado";saveProfile();return "Ritmo ajustado.";}
  if(text=="/memoria")return "Nome: "+userName+" | Gosto: "+interest+" | Exemplos: "+String(lessonCount)+" | Humor: "+FACE_NAMES[robot.mood];
  if(text.startsWith("/corrigir "))return teachText(lastText,labelIndex(text.substring(10)));
  if(text=="/apagar") {
    userName="";interest="";lively=false;lastText="";lessonCount=0;memset(lessons,0,sizeof(lessons));
    robot.setMood(NEUTRAL,now);saveProfile();saveLessons();saveMood();return "Memoria pessoal apagada. A senha do Wi-Fi foi preservada.";
  }
  if(text.startsWith("/"))return "Comando desconhecido. Envie /ajuda.";
  lastText=text;
  int label=rememberedLabel(lessons,lessonCount,text.c_str());
  bool remembered=label>=0;
  if(label<0)label=classificar(text.c_str()).classe;
  // Neutral text wakes the robot without immediately erasing its accumulated mood.
  if(label<3)robot.setMood((Face)label,now);
  else robot.pose(CURIOUS,now);
  saveMood();
  const char* const answers[4][3]={
    {"Que bom! Vamos comemorar!","Gostei dessa energia!","Fico contente por voce!"},
    {"Parece um dia dificil. Quer contar mais?","Sinto muito. Um passo de cada vez.","Estou aqui para acompanhar voce."},
    {"Parece frustrante. Vamos fazer uma pausa?","Quer contar o que aconteceu?","Vamos respirar e ir com calma."},
    {"Estou ouvindo. Como voce esta se sentindo?","Nao reconheci uma emocao clara. Pode explicar de outro jeito?","Vamos conversar um pouco?"}
  };
  String reply=userName.isEmpty()?"":userName+", ";
  reply+=answers[label][replies[label]++%3];
  if(!interest.isEmpty() && label==0)reply+=" Talvez seja hora de aproveitar "+interest+".";
  reply+=" [";reply+=NOMES[label];reply+=remembered?"; aprendido]":"]";
  return reply;
}
void drawEyes(uint32_t now) {
  if(!oledReady || now-frameAt<33)return;frameAt=now;
  Face f=robot.face();
  if(now-gazeAt>(lively?1700u:3600u)) {gazeAt=now;gazeTarget=(f==SLEEPY?0:(int)random(-5,6));}
  gazeX+=(gazeTarget-gazeX)*.14f;
  if(!blinking && now-blinkAt>(lively?2400u:4400u)) {blinking=true;blinkStart=now;blinkAt=now;}
  float close=0;
  if(blinking){uint32_t dt=now-blinkStart;if(dt>=220)blinking=false;else close=dt<110?dt/110.f:(220-dt)/110.f;}
  float targetHeight=f==SLEEPY?7:f==SURPRISE?43:32;
  eyeHeight+=(targetHeight-eyeHeight)*.18f;
  brow+=((f==ANGRY?17.f:f==SAD?-16.f:0)-brow)*.18f;
  smile+=((f==HAPPY?1.f:0)-smile)*.18f;
  asymmetry+=((f==CURIOUS?12.f:0)-asymmetry)*.18f;
  display.clearDisplay();
  for(int side=0;side<2;++side){
    int x=(side?72:20)+(int)gazeX;
    int h=max(3,(int)((eyeHeight-(side?asymmetry:0))*(1-close)));
    int y=32-h/2;
    display.fillRoundRect(x,y,36,h,min(9,h/2),SSD1306_WHITE);
    if(h>12 && close<.35f){
      int cut=abs((int)brow);
      bool inside=brow>0;
      if(cut>0){bool right=(side==0)==inside;display.fillTriangle(x,y,x+35,y,right?x+35:x,y+min(cut,h-4),SSD1306_BLACK);}
      if(smile>.05f)display.fillCircle(x+18,y+h+14-(int)(smile*10),21,SSD1306_BLACK);
    }
  }
  display.display();
}
void jsonError(int code,const String& msg){server.send(code,"application/json; charset=utf-8","{\"error\":"+jsonString(msg)+"}");}
bool allowPost(){
  if(server.header("X-Robo-Client")!="1"){jsonError(403,"Use o painel da placa.");return false;}
  return true;
}
void setupWeb(){
  const char* headers[]={"X-Robo-Client"};server.collectHeaders(headers,1);
  server.on("/",HTTP_GET,[]{server.sendHeader("Cache-Control","no-store");server.send_P(200,"text/html; charset=utf-8",WEB_UI);});
  server.on("/api/status",HTTP_GET,[]{server.sendHeader("Cache-Control","no-store");server.send(200,"application/json; charset=utf-8",stateJson());});
  server.on("/api/message",HTTP_POST,[]{
    if(!allowPost())return;
    String s=server.arg("text");if(!validField(s,240)||s.isEmpty()){jsonError(400,"Envie uma frase de ate 240 bytes, em uma linha.");return;}
    String reply=respond(s);server.send(200,"application/json; charset=utf-8","{\"reply\":"+jsonString(reply)+",\"state\":"+stateJson()+"}");
  });
  server.on("/api/profile",HTTP_POST,[]{
    if(!allowPost())return;
    String name=server.arg("name"),like=server.arg("interest"),pace=server.arg("pace");name.trim();like.trim();
    if(!validField(name,24)||!validField(like,40)||(pace!="calmo"&&pace!="animado")){jsonError(400,"Nome: ate 24 bytes; preferencia: ate 40 bytes; ritmo calmo ou animado.");return;}
    userName=name;interest=like;lively=pace=="animado";saveProfile();robot.wake(millis());
    server.send(200,"application/json; charset=utf-8",stateJson());
  });
  server.on("/api/teach",HTTP_POST,[]{
    if(!allowPost())return;
    String t=server.arg("text");int label=labelIndex(server.arg("label"));
    char norm[241];normalizeText(t.c_str(),norm,sizeof(norm));
    if(!validField(t,240)||!*norm||label<0){jsonError(400,"Envie uma frase valida e uma das quatro classes.");return;}
    teachText(t,label);server.send(200,"application/json; charset=utf-8",stateJson());
  });
  server.onNotFound([]{jsonError(404,"Nao encontrado.");});server.begin();
}
void setup(){
  Serial.begin(115200);pinMode(0,INPUT_PULLUP);
  memoryReady=prefs.begin("robo-olhos",false);
  if(memoryReady){
    userName=prefs.getString("name","");interest=prefs.getString("interest","");lively=prefs.getBool("lively",false);
    uint8_t m=prefs.getUChar("mood",3),strength=prefs.getUChar("strength",0);
    if(m<3&&strength>0&&strength<=3){robot.mood=(Face)m;robot.intensity=strength;}
    size_t bytes=prefs.getBytesLength("lessons");
    if(bytes && bytes<=sizeof(lessons) && bytes%sizeof(Lesson)==0){prefs.getBytes("lessons",lessons,bytes);lessonCount=bytes/sizeof(Lesson);for(int i=0;i<lessonCount;++i){lessons[i].text[240]=0;if(lessons[i].label>3)lessons[i].label=3;}}
    apPassword=prefs.getString("ap-pass","");
  }
  if(apPassword.length()!=12){char pw[13];snprintf(pw,sizeof(pw),"%08lx%04lx",(unsigned long)esp_random(),(unsigned long)(esp_random()&0xffff));apPassword=pw;if(memoryReady)prefs.putString("ap-pass",apPassword);}
  char ssid[32];snprintf(ssid,sizeof(ssid),"RoboOlhos-%04lx",(unsigned long)(ESP.getEfuseMac()&0xffff));apName=ssid;
  Wire.begin(8,9);Wire.setClock(400000);
  for(uint8_t addr=0x3c;addr<=0x3d;++addr){Wire.beginTransmission(addr);if(Wire.endTransmission()==0){oledReady=display.begin(SSD1306_SWITCHCAPVCC,addr,true,false);break;}}
  WiFi.mode(WIFI_AP);bool apReady=WiFi.softAP(apName.c_str(),apPassword.c_str(),1,false,2);
  setupBluetooth(apName);
  setupWeb();robot.wake(millis());robot.lastDecay=millis();saveMood();
  delay(1000);
  Serial.println("\nROBO OLHOS — IA local. 115200 baud + Nova linha. /ajuda para comandos.");
  Serial.println("Rede: "+apName+" | Senha: "+apPassword+" | http://192.168.4.1");
  Serial.println("Bluetooth LE: "+apName+" (use o app Robo Olhos)");
  if(!apReady)Serial.println("Falha ao iniciar Wi-Fi. A serial continua funcionando.");
  if(!oledReady)Serial.println("OLED nao encontrado; confira GPIO8/9, 3V3 e GND.");
  if(!memoryReady)Serial.println("NVS indisponivel: memoria apenas nesta sessao.");
}
void loop(){
  server.handleClient();
  handleBluetooth();
  int batch=0;
  while(Serial.available()&&batch++<64){
    char c=Serial.read();
    if(c=='\r'||c=='\n'){
      if(serialOverflow)Serial.println("Mensagem descartada: limite de 240 bytes.");
      else if(serialLength){serialLine[serialLength]=0;Serial.println(respond(serialLine));}
      serialLength=0;serialOverflow=false;
    }else if(c && !serialOverflow){if(serialLength<240)serialLine[serialLength++]=c;else serialOverflow=true;}
  }
  uint32_t now=millis();int event=button.update(digitalRead(0)==LOW,now);
  if(event==1){if(robot.sleeping)robot.wake(now);else robot.setMood((Face)((robot.mood+1)%4),now);}
  else if(event==2){bool wasDemo=robot.demo;robot.wake(now);robot.demo=!wasDemo;robot.lastDemo=now;robot.demoFace=0;}
  robot.tick(now);saveMood();drawEyes(now);delay(2);
}
