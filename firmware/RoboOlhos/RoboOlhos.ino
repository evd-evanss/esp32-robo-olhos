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
Dialogue dialogues[MAX_DIALOGUES] = {};
uint8_t lessonCount=0;
uint8_t dialogueCount=0;
bool memoryReady=false, oledReady=false, lively=false;
String userName, interest, apName, apPassword, lastText;
uint32_t frameAt=0, blinkAt=0, blinkStart=0, gazeAt=0, nextBlink=3200, nextGaze=3200;
bool blinking=false,doubleBlinkPending=false;
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
void saveDialogues() {
  if(memoryReady) {
    if(dialogueCount) prefs.putBytes("dialogue",dialogues,sizeof(Dialogue)*dialogueCount);
    else prefs.remove("dialogue");
  }
}
String stateJson() {
  return "{\"face\":"+jsonString(FACE_NAMES[robot.face()])+",\"mood\":"+jsonString(FACE_NAMES[robot.mood])+",\"interaction\":"+jsonString(INTERACTION_NAMES[robot.interaction])+",\"name\":"+jsonString(userName)+",\"interest\":"+jsonString(interest)+",\"pace\":"+jsonString(lively?"animado":"calmo")+",\"demo\":"+(robot.demo?"true":"false")+",\"memory\":"+(memoryReady?"true":"false")+",\"lessons\":"+String(lessonCount)+",\"dialogues\":"+String(dialogueCount)+"}";
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
  if(text=="/ajuda")return "Converse normalmente ou ensine no app uma resposta e expressao. Comandos: /demo /neutro /surpresa /curioso /dormir /nome Ana /gosto musica /memoria /corrigir feliz|triste|raiva|neutro /apagar /wifi";
  if(text=="/wifi")return "Rede: "+apName+" | Senha: "+apPassword+" | Abra http://192.168.4.1";
  if(text.startsWith("/estado ")) {
    String value=text.substring(8);int selected=-1;for(int i=0;i<5;++i)if(value==INTERACTION_NAMES[i])selected=i;
    if(selected<0)return "Estado desconhecido.";
    uint32_t duration=selected==MODE_LISTENING?10000:selected==MODE_THINKING?12000:selected==MODE_SPEAKING?6000:selected==MODE_REACTING?3000:0;
    robot.setInteraction((Interaction)selected,now,duration);return "";
  }
  if(text=="/demo") {robot.demo=true;robot.demoFace=0;robot.lastDemo=now;return "Demonstracao ligada. Envie uma frase para sair.";}
  if(text=="/neutro") {robot.setMood(NEUTRAL,now);saveMood();return "De volta ao normal.";}
  if(text=="/surpresa" || text=="/curioso") {robot.pose(text=="/surpresa"?SURPRISE:CURIOUS,now);return "Olha minha expressao!";}
  if(text=="/dormir") {robot.lastActivity=now-SLEEP_AFTER;robot.sleeping=true;return "Vou descansar. Uma mensagem ou toque em BOOT me acorda.";}
  if(text.startsWith("/nome ")) {String v=text.substring(6);v.trim();if(!validField(v,24))return "Nome: ate 24 bytes, sem caracteres de controle.";userName=v;saveProfile();return "Combinado, "+userName+"!";}
  if(text.startsWith("/gosto ")) {String v=text.substring(7);v.trim();if(!validField(v,40))return "Preferencia: ate 40 bytes, sem caracteres de controle.";interest=v;saveProfile();return "Vou lembrar que voce gosta de "+interest+".";}
  if(text.startsWith("/ritmo ")) {String v=text.substring(7);if(v!="calmo" && v!="animado")return "Use /ritmo calmo ou /ritmo animado.";lively=v=="animado";saveProfile();return "Ritmo ajustado.";}
  if(text=="/memoria")return "Nome: "+userName+" | Gosto: "+interest+" | Emocoes: "+String(lessonCount)+" | Conversas: "+String(dialogueCount)+" | Humor: "+FACE_NAMES[robot.mood];
  if(text.startsWith("/ensinar|")) {
    int a=text.indexOf('|',9),b=a<0?-1:text.indexOf('|',a+1);
    if(a<0||b<0)return "Treino invalido. Use o formulario do app.";
    String face=text.substring(9,a),prompt=text.substring(a+1,b),reply=text.substring(b+1); prompt.trim();reply.trim();
    int chosen=-1;for(int i=0;i<7;++i)if(face==FACE_NAMES[i])chosen=i;
    if(chosen<0||prompt.isEmpty()||prompt.length()>80||reply.isEmpty()||reply.length()>120||prompt.indexOf('|')>=0||reply.indexOf('|')>=0)return "Use pergunta ate 80, resposta ate 120 e sem o caractere |.";
    if(!learnDialogue(dialogues,dialogueCount,prompt.c_str(),reply.c_str(),chosen))return "Nao consegui salvar esse treino.";
    saveDialogues();robot.pose((Face)chosen,now);return "Aprendi. Vou usar essa resposta e expressao em frases parecidas.";
  }
  if(text.startsWith("/corrigir "))return teachText(lastText,labelIndex(text.substring(10)));
  if(text=="/apagar") {
    userName="";interest="";lively=false;lastText="";lessonCount=0;dialogueCount=0;memset(lessons,0,sizeof(lessons));memset(dialogues,0,sizeof(dialogues));
    robot.setMood(NEUTRAL,now);saveProfile();saveLessons();saveDialogues();saveMood();return "Memoria pessoal apagada. A senha do Wi-Fi foi preservada.";
  }
  if(text.startsWith("/"))return "Comando desconhecido. Envie /ajuda.";
  lastText=text;
  int dialogue=findDialogue(dialogues,dialogueCount,text.c_str());
  if(dialogue>=0) {robot.setInteraction(MODE_REACTING,now,3000);robot.pose((Face)dialogues[dialogue].face,now);return String(dialogues[dialogue].reply);}
  int label=rememberedLabel(lessons,lessonCount,text.c_str());
  bool remembered=label>=0;
  if(label<0)label=classificar(text.c_str()).classe;
  // Neutral text wakes the robot without immediately erasing its accumulated mood.
  if(label<3)robot.setMood((Face)label,now);
  else robot.pose(CURIOUS,now);
  robot.setInteraction(MODE_REACTING,now,3000);
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
  Interaction interaction=robot.interaction;
  if(interaction==MODE_LISTENING) gazeTarget=0;
  else if(interaction==MODE_THINKING) gazeTarget=((now/650)%2)?-7:7;
  else if(now-gazeAt>nextGaze) {gazeAt=now;nextGaze=random(lively?1200:2600,lively?2400:4600);gazeTarget=(f==SLEEPY?0:(int)random(-7,8));}
  gazeX+=(gazeTarget-gazeX)*.14f;
  if(!blinking && now-blinkAt>nextBlink) {blinking=true;blinkStart=now;blinkAt=now;}
  float close=0;
  if(blinking){
    uint32_t dt=now-blinkStart;
    if(dt>=180){
      blinking=false;blinkAt=now;
      if(doubleBlinkPending){doubleBlinkPending=false;nextBlink=random(lively?1800:2800,lively?3200:5200);}
      else if(random(0,5)==0){doubleBlinkPending=true;nextBlink=150;}
      else nextBlink=random(lively?1800:2800,lively?3200:5200);
    } else close=dt<90?dt/90.f:(180-dt)/90.f;
  }
  float pulse=sinf(now*(f==SURPRISE?.012f:.004f));
  float voicePulse=interaction==MODE_SPEAKING?sinf(now*.018f)*5:0;
  float listenPulse=interaction==MODE_LISTENING?sinf(now*.009f)*4:0;
  float targetHeight=f==SLEEPY?6:f==SURPRISE?42+pulse*3:f==HAPPY?27:f==ANGRY?25:f==SAD?29:32;
  targetHeight+=voicePulse+listenPulse;
  eyeHeight+=(targetHeight-eyeHeight)*.18f;
  brow+=((f==ANGRY?19.f:f==SAD?-18.f:0)-brow)*.18f;
  smile+=((f==HAPPY?1.f:0)-smile)*.18f;
  float thinkingAsymmetry=interaction==MODE_THINKING?(((now/650)%2)?14.f:-14.f):0;
  asymmetry+=((f==CURIOUS?15.f:0)+thinkingAsymmetry-asymmetry)*.18f;
  display.clearDisplay();
  for(int side=0;side<2;++side){
    int curiousShift=f==CURIOUS?(side?3:-3):0;
    if(interaction==MODE_LISTENING) curiousShift+=(int)(sinf(now*.009f)*(side?2:-2));
    int angryShake=f==ANGRY?(int)(sinf(now*.025f+side)*1.5f):0;
    int x=(side?72:20)+(int)gazeX+curiousShift+angryShake;
    int h=max(3,(int)((eyeHeight-(side?asymmetry:0))*(1-close)));
    int bounce=f==HAPPY?(int)(pulse*(interaction==MODE_REACTING?4:2)):f==SAD?2:interaction==MODE_SPEAKING?(int)(sinf(now*.018f)*2):0;
    int y=32-h/2+bounce;
    int width=f==SURPRISE?34:36;
    if(f==HAPPY && close<.55f) {
      // Two thick rising crescents: a much clearer smile using eyes only.
      int centerX=x+width/2, centerY=35+bounce;
      display.fillCircle(centerX,centerY,20,SSD1306_WHITE);
      display.fillCircle(centerX,centerY+10,17,SSD1306_BLACK);
      display.fillRect(centerX-22,centerY+8,44,18,SSD1306_BLACK);
    } else {
      display.fillRoundRect(x,y,width,h,min(10,h/2),SSD1306_WHITE);
    }
    if(h>12 && close<.35f){
      int cut=abs((int)brow);
      bool inside=brow>0;
      if(cut>0){bool right=(side==0)==inside;display.fillTriangle(x,y,x+width-1,y,right?x+width-1:x,y+min(cut,h-4),SSD1306_BLACK);}
      if(smile>.05f && f!=HAPPY)display.fillCircle(x+width/2,y+h+14-(int)(smile*10),21,SSD1306_BLACK);
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
    size_t dialogueBytes=prefs.getBytesLength("dialogue");
    if(dialogueBytes && dialogueBytes<=sizeof(dialogues) && dialogueBytes%sizeof(Dialogue)==0){prefs.getBytes("dialogue",dialogues,dialogueBytes);dialogueCount=dialogueBytes/sizeof(Dialogue);for(int i=0;i<dialogueCount;++i){dialogues[i].prompt[80]=0;dialogues[i].reply[120]=0;if(dialogues[i].face>6)dialogues[i].face=3;}}
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
