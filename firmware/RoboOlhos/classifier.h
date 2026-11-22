#pragma once
#include "model.h"
// Inferencia local: Naive Bayes aprendido com exemplos em portugues.
// Nao requer TensorFlow, rede, API, microfone ou PSRAM.
#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include <stdio.h>

constexpr size_t MAX_TEXTO = 240;  // bytes UTF-8
constexpr int MAX_PALAVRAS = 48;
constexpr int MAX_PALAVRA = 24;
const char* const NOMES[] = {"feliz", "triste", "raiva", "neutro"};

struct Resultado {
  uint8_t classe;
  int reconhecidas;
  float margem;
};

bool igual(const char* a, const char* b) { return strcmp(a,b) == 0; }
bool stopWord(const char* s) {
  static const char* const stops[] = {"eu","estou","me","com","de","do","da","o","a","os","as","e","um","uma","hoje","muito","voce","isso","que","sinto"};
  for (const char* w : stops) if (igual(s,w)) return true;
  return false;
}

// Normaliza letras portuguesas UTF-8, sem alocacao dinamica.
// Outros caracteres delimitam palavras. Acentos combinantes sao ignorados.
char proximaLetra(const char*& p) {
  uint32_t cp = (uint8_t)*p++;
  if (cp < 128) {
    if (cp >= 'A' && cp <= 'Z') cp += 32;
    return ((cp >= 'a' && cp <= 'z') || (cp >= '0' && cp <= '9')) ? (char)cp : ' ';
  }
  int extra = 0;
  if ((cp & 0xE0) == 0xC0) { cp &= 0x1F; extra=1; }
  else if ((cp & 0xF0) == 0xE0) { cp &= 0x0F; extra=2; }
  else if ((cp & 0xF8) == 0xF0) { cp &= 0x07; extra=3; }
  else return ' ';
  for (int i=0; i<extra; ++i) {
    if (!*p || (((uint8_t)*p & 0xC0) != 0x80)) return ' ';
    cp = (cp << 6) | ((uint8_t)*p++ & 0x3F);
  }
  if (cp >= 0x300 && cp <= 0x36F) return '\1';
  if (cp >= 0xC0 && cp <= 0xDE && cp != 0xD7) cp += 32;
  if (cp >= 0xE0 && cp <= 0xE5) return 'a';
  if (cp == 0xE7) return 'c';
  if (cp >= 0xE8 && cp <= 0xEB) return 'e';
  if (cp >= 0xEC && cp <= 0xEF) return 'i';
  if (cp == 0xF1) return 'n';
  if (cp >= 0xF2 && cp <= 0xF6) return 'o';
  if (cp >= 0xF9 && cp <= 0xFC) return 'u';
  if (cp == 0xFD || cp == 0xFF) return 'y';
  return ' ';
}
int buscar(const char* feature) {
  int inicio=0, fim=VOCAB_SIZE-1;
  while (inicio<=fim) {
    int meio=(inicio+fim)/2;
    int c=strcmp(feature,VOCAB[meio]);
    if (!c) return meio;
    if (c<0) fim=meio-1; else inicio=meio+1;
  }
  return -1;
}
void registrar(const char* f, bool* vistos) {
  int i=buscar(f);
  if (i>=0) vistos[i]=true;
}
Resultado classificar(const char* texto) {
  bool vistos[VOCAB_SIZE] = {};
  char anterior[28] = {};
  const char* p=texto;
  int negacao=0, palavras=0;
  while (*p && palavras<MAX_PALAVRAS) {
    char palavra[MAX_PALAVRA] = {};
    int tamanho=0;
    while (*p) {
      char c=proximaLetra(p);
      if (c=='\1') continue;
      if (c==' ') { if (tamanho) break; else continue; }
      if (tamanho<MAX_PALAVRA-1) palavra[tamanho++]=c;
    }
    if (!tamanho) continue;
    ++palavras;
    char atual[28] = {};
    if (igual(palavra,"nao") || igual(palavra,"nunca") || igual(palavra,"nem")) {
      negacao=3; snprintf(atual,sizeof(atual),"%s",palavra);
    } else if (igual(palavra,"mas")) {
      negacao=0; snprintf(atual,sizeof(atual),"%s",palavra);
    } else if (negacao) {
      --negacao; snprintf(atual,sizeof(atual),"n_%s",palavra);
    } else snprintf(atual,sizeof(atual),"%s",palavra);
    if (!stopWord(atual)) registrar(atual,vistos);
    if (*anterior) {
      char par[60]; snprintf(par,sizeof(par),"%s_%s",anterior,atual);
      registrar(par,vistos);
    }
    snprintf(anterior,sizeof(anterior),"%s",atual);
  }
  float scores[4] = {};
  int reconhecidas=0;
  bool evidencias[3] = {};
  for (int i=0; i<VOCAB_SIZE; ++i) {
    if (!vistos[i]) continue;
    ++reconhecidas;
    for (int k=0; k<4; ++k) scores[k]+=PESOS[i][k];
    if (!strchr(VOCAB[i],'_')) {
      int melhor=0;
      for (int k=1; k<4; ++k) if (PESOS[i][k]>PESOS[i][melhor]) melhor=k;
      float segundo=-1e9f;
      for (int k=0; k<4; ++k) if (k!=melhor && PESOS[i][k]>segundo) segundo=PESOS[i][k];
      if (melhor<3 && PESOS[i][melhor]-segundo>=0.8f) evidencias[melhor]=true;
    }
  }
  int melhor=0;
  for (int k=1; k<4; ++k) if (scores[k]>scores[melhor]) melhor=k;
  float segundo=-1e9f;
  for (int k=0; k<4; ++k) if (k!=melhor && scores[k]>segundo) segundo=scores[k];
  float margem=scores[melhor]-segundo;
  int n=evidencias[0]+evidencias[1]+evidencias[2];
  uint8_t classe=(reconhecidas && margem>=0.8f && n<2) ? melhor : 3;
  return {classe,reconhecidas,margem};
}
