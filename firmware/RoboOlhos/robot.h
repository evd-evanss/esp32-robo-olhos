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
constexpr int MAX_DIALOGUES=10;
struct Dialogue { char prompt[81]; char reply[121]; uint8_t face; };
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

int normalizedWordCount(const char* text) {
  if (!*text) return 0;
  int count=1; for (;*text;++text) if (*text==' ') ++count; return count;
}
bool hasNormalizedWord(const char* text,const char* word,size_t length) {
  for (const char* p=text;*p;) {
    while (*p==' ') ++p; const char* start=p; while (*p && *p!=' ') ++p;
    if ((size_t)(p-start)==length && !strncmp(start,word,length)) return true;
  }
  return false;
}
int dialogueScore(const char* learned,const char* spoken) {
  if (igual(learned,spoken)) return 100;
  int learnedWords=normalizedWordCount(learned), spokenWords=normalizedWordCount(spoken), matches=0;
  for (const char* p=learned;*p;) {
    while (*p==' ') ++p; const char* start=p; while (*p && *p!=' ') ++p;
    size_t length=p-start; if (length>2 && hasNormalizedWord(spoken,start,length)) ++matches;
  }
  int largest=learnedWords>spokenWords?learnedWords:spokenWords;
  return largest ? matches*100/largest : 0;
}
int findDialogue(const Dialogue* dialogues,int count,const char* text) {
  char key[81]; normalizeText(text,key,sizeof(key));
  int best=-1,bestScore=0;
  for (int i=0;i<count;++i) {
    int score=dialogueScore(dialogues[i].prompt,key);
    if (score>bestScore) { bestScore=score; best=i; }
  }
  return bestScore>=60 ? best : -1;
}
bool learnDialogue(Dialogue* dialogues,uint8_t& count,const char* prompt,const char* reply,uint8_t face) {
  if (face>6 || !reply[0] || strlen(reply)>120) return false;
  char key[81]; normalizeText(prompt,key,sizeof(key)); if (!key[0]) return false;
  for (int i=0;i<count;++i) if (igual(key,dialogues[i].prompt)) {
    snprintf(dialogues[i].reply,sizeof(dialogues[i].reply),"%s",reply); dialogues[i].face=face; return true;
  }
  if (count==MAX_DIALOGUES) { memmove(dialogues,dialogues+1,sizeof(Dialogue)*(MAX_DIALOGUES-1)); --count; }
  snprintf(dialogues[count].prompt,sizeof(dialogues[count].prompt),"%s",key);
  snprintf(dialogues[count].reply,sizeof(dialogues[count].reply),"%s",reply);
  dialogues[count++].face=face; return true;
}
