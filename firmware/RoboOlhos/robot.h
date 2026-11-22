#pragma once
#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include "classifier.h"

enum Face : uint8_t { HAPPY, SAD, ANGRY, NEUTRAL, SURPRISE, CURIOUS, SLEEPY };
const char* const FACE_NAMES[] = {"feliz","triste","raiva","neutro","surpresa","curioso","sono"};
constexpr uint32_t SLEEP_AFTER=45000, MOOD_DECAY=30000;
struct Robot {
  Face mood=NEUTRAL, overrideFace=NEUTRAL;
  uint8_t intensity=0;
  bool demo=false, sleeping=false;
  uint32_t lastActivity=0, lastDecay=0, overrideUntil=0, lastDemo=0;
  uint8_t demoFace=0;
  bool hasOverride=false;
  void wake(uint32_t now) { sleeping=false; lastActivity=now; }
  void setMood(Face f,uint32_t now) {
    wake(now); demo=false; hasOverride=false;
    if (f==NEUTRAL) { mood=f; intensity=0; }
    else { intensity=mood==f ? (intensity<3 ? intensity+1 : 3) : 1; mood=f; }
    lastDecay=now;
  }
  void pose(Face f,uint32_t now) { wake(now); demo=false; hasOverride=true; overrideFace=f; overrideUntil=now+2500; }
  void tick(uint32_t now) {
    if (hasOverride && (int32_t)(now-overrideUntil)>=0) hasOverride=false;
    if (intensity && now-lastDecay>=MOOD_DECAY) {
      lastDecay=now; --intensity; if (!intensity) mood=NEUTRAL;
    }
    if (demo && now-lastDemo>=2500) { lastDemo=now; demoFace=(demoFace+1)%7; }
    sleeping=!demo && now-lastActivity>=SLEEP_AFTER;
  }
  Face face() const { return demo ? (Face)demoFace : sleeping ? SLEEPY : hasOverride ? overrideFace : mood; }
};
// Debounce and one event per press. A long press never emits a short press.
struct BootButton {
  bool raw=false, stable=false, longSent=false;
  uint32_t changed=0, pressed=0;
  int update(bool down,uint32_t now) {
    if (down!=raw) { raw=down; changed=now; }
    if (raw!=stable && now-changed>=30) {
      stable=raw;
      if (stable) { pressed=now; longSent=false; }
      else if (!longSent) return 1;
    }
    if (stable && !longSent && now-pressed>=1200) { longSent=true; return 2; }
    return 0;
  }
};
constexpr int MAX_LESSONS=12;
struct Lesson { char text[241]; uint8_t label; };
// Plain normalized words, punctuation and case independent.
void normalizeText(const char* src,char* dst,size_t cap) {
  size_t n=0; bool space=true;
  while (*src && n+1<cap) {
    char c=proximaLetra(src);
    if (c=='\1') continue;
    if (c==' ') { if (!space) { dst[n++]=' '; space=true; } }
    else { dst[n++]=c; space=false; }
  }
  if (n && dst[n-1]==' ') --n;
  dst[n]=0;
}
// Deliberately conservative: corrections match the normalized whole sentence.
// This avoids a learned 'nao estou feliz' overriding 'estou feliz'.
int rememberedLabel(const Lesson* lessons,int count,const char* text) {
  char key[241]; normalizeText(text,key,sizeof(key));
  for (int i=count-1;i>=0;--i) if (igual(key,lessons[i].text)) return lessons[i].label;
  return -1;
}
bool learn(Lesson* lessons,uint8_t& count,const char* text,uint8_t label) {
  if (label>3) return false;
  char key[241]; normalizeText(text,key,sizeof(key)); if (!key[0]) return false;
  for (int i=0;i<count;++i) if (igual(key,lessons[i].text)) { lessons[i].label=label; return true; }
  if (count==MAX_LESSONS) { memmove(lessons,lessons+1,sizeof(Lesson)*(MAX_LESSONS-1)); --count; }
  snprintf(lessons[count].text,sizeof(lessons[count].text),"%s",key);
  lessons[count++].label=label; return true;
}
