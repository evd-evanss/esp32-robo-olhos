#include "../firmware/RoboOlhos/robot.h"
#include <cassert>
#include <iostream>
int main() {
 Robot r;
 for(int i=0;i<5;i++) r.setMood(HAPPY,100);
 assert(r.intensity==3);
 r.tick(30100); assert(r.intensity==2);
 r.pose(CURIOUS,30200); assert(r.face()==CURIOUS);
 r.tick(32700); assert(r.face()==HAPPY);
 r.tick(75200); assert(r.face()==SLEEPY);
 r.setMood(ANGRY,76000); assert(r.face()==ANGRY && r.intensity==1);
 r.setMood(NEUTRAL,77000); assert(r.intensity==0);
 r.pose(SURPRISE,0xfffffff0u);r.tick(0x1000);assert(!r.hasOverride);
 BootButton b;
 assert(!b.update(true,100));assert(!b.update(true,130));
 assert(!b.update(false,200));assert(b.update(false,230)==1);
 assert(!b.update(true,300));assert(!b.update(true,330));
 assert(b.update(true,1530)==2);assert(!b.update(false,1600));assert(!b.update(false,1630));
 Lesson lessons[MAX_LESSONS]={};uint8_t n=0;
 assert(learn(lessons,n,"NÃO estou feliz!",1));
 assert(rememberedLabel(lessons,n,"nao estou feliz")==1);
 assert(rememberedLabel(lessons,n,"estou feliz")==-1);
 assert(learn(lessons,n,"nao estou feliz",3));assert(n==1);
 assert(!learn(lessons,n,"!!!",0));assert(!learn(lessons,n,"oi",4));
 for(int i=0;i<20;i++){char t[30];snprintf(t,sizeof(t),"frase %d",i);assert(learn(lessons,n,t,0));}
 assert(n==12);assert(rememberedLabel(lessons,n,"frase 0")==-1);
 assert(rememberedLabel(lessons,n,"frase 19")==0);
 assert(classificar("estou feliz").classe==0);
 assert(classificar("estou triste").classe==1);
 assert(classificar("estou com raiva").classe==2);
 assert(classificar("xyzzy").classe==3);
 assert(classificar("não estou feliz").classe==3);
 assert(classificar("estou feliz e triste").classe==3);
 std::cout << "OK: humor, temporizadores, botao, memoria e classificacao\n";
}
