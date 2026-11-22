#!/usr/bin/env python3
"""Treina um Naive Bayes pequeno; apenas biblioteca padrao do Python.
Sem argumentos, atualiza os parametros dentro de Olhos_IA_Local.ino.
Com um caminho como argumento, exporta somente um cabecalho C++.
Dados didaticos escritos para este projeto; nao e avaliacao psicologica.
"""
import collections
import json
import math
import pathlib
import unicodedata

CLASSES = ['feliz', 'triste', 'raiva', 'neutro']
DADOS = {
'feliz': '''estou feliz|que alegria|estou muito contente|hoje estou animado|estou alegre|me sinto bem|estou de bom humor|que maravilha|adorei isso|amei o resultado|isso e otimo|foi incrivel|estou empolgado|estou satisfeito|estou orgulhoso de mim|consegui terminar|deu certo|passei na prova|ganhei um presente|estou sorrindo|quero comemorar|estou apaixonado|gostei muito|obrigado pela ajuda|voce me fez sorrir|me sinto amado|estou aliviado|que dia maravilhoso|venci o desafio|estou radiante|estou feliz com voce|que noticia boa|nao estou triste|nao estou com raiva|nao estou bravo|nao estou chateado|nao estou irritado|nao me sinto triste|nao fiquei triste|nao estou mais triste''',
'triste': '''estou triste|estou muito triste|me sinto sozinho|estou sozinha|estou desanimado|estou chateado|estou decepcionado|quero chorar|estou chorando|sinto saudade|estou com saudades|estou magoado|me sinto mal|hoje foi dificil|foi um dia ruim|perdi meu amigo|meu cachorro morreu|estou de luto|ninguem gosta de mim|me sinto abandonado|estou sem vontade|estou pra baixo|que tristeza|estou deprimido|estou infeliz|estou com o coracao partido|me sinto vazio|estou cansado de tudo|sinto uma dor no coracao|recebi uma noticia ruim|nao estou feliz|nao estou contente|nao estou animado|nao me sinto bem|nao gostei do resultado|nao deu certo|nao consegui terminar|nao estou alegre|nao fiquei feliz|nao estou muito feliz''',
'raiva': '''estou com raiva|estou bravo|estou brava|estou irritado|estou irritada|estou furioso|estou furiosa|estou revoltado|que odio|odeio isso|estou indignado|isso me irrita|isso me deixa bravo|que absurdo|isso e injusto|me sinto injusticado|estou frustrado|perdi a paciencia|estou sem paciencia|pare com isso|chega disso|me deixa em paz|estou de saco cheio|estou de mau humor|quero gritar|isso me enfurece|estou nervoso com voce|que raiva disso|fiquei bravo com isso|me enganaram|estou com muita raiva|estou muito bravo|estou muito irritado|sinto odio|nao aguento essa injustica|fizeram uma injustica comigo|odeio quando isso acontece|isso e uma porcaria|estou zangado|estou zangada''',
'neutro': '''oi|ola|bom dia|boa tarde|boa noite|tudo bem|como voce esta|qual e seu nome|quem e voce|o que voce faz|me conte algo|voce esta ai|estou aqui|estou tranquilo|estou calma|estou normal|nada demais|tanto faz|estou pensando|nao sei|estou em casa|vou trabalhar|vou estudar|estou lendo|preciso de agua|hoje e domingo|qual e a hora|quanto e dois mais dois|como funciona o wifi|quero testar o display|acenda os olhos|vamos conversar|meu nome e pedro|hoje esta chovendo|voce gosta de musica|me fale sobre tecnologia|estou esperando|estou curioso|estou com sono|estou com fome'''
}
DADOS = {k:v.split('|') for k,v in DADOS.items()}
# Negar uma emocao nao implica sentir a emocao oposta.
for classe in ('feliz', 'triste'):
    negativas = [f for f in DADOS[classe] if f.startswith('nao ')]
    DADOS[classe] = [f for f in DADOS[classe] if f not in negativas]
    DADOS['neutro'].extend(negativas)
DADOS['feliz'].extend('feliz|alegria|contente|animado|alegre|maravilhoso|adorei|amei|otimo|incrivel|empolgado|satisfeito|orgulhoso|radiante|estou feliz demais|estou contente hoje|me sinto alegre agora|fiquei empolgado com a noticia|estou animada|estou satisfeita'.split('|'))
DADOS['triste'].extend('triste|tristeza|sozinho|sozinha|desanimado|chateado|decepcionado|chorando|saudade|magoado|infeliz|abandonado|desanimada|decepcionada|magoada|estou me sentindo triste|estou sozinho em casa|estou decepcionada com isso|me sinto desanimada|estou chateada'.split('|'))
DADOS['raiva'].extend('raiva|bravo|brava|irritado|irritada|furioso|furiosa|revoltado|odio|indignado|frustrado|zangado|zangada|revoltada|indignada|frustrada|estou com raiva agora|estou sentindo raiva|sinto muita raiva|que situacao irritante'.split('|'))
DADOS['triste'].extend('estou chateada com isso|fiquei decepcionado|me sinto decepcionada|estou me sentindo mal|estou desanimada hoje|me sinto sozinha|sinto falta dos meus amigos|hoje estou triste e cansado'.split('|'))
DADOS['raiva'].extend('me sinto frustrada|me sinto frustrado|isso e irritante|estou indignada com isso|fiquei zangado|fiquei irritada|essa situacao me revolta|que injustica'.split('|'))
DADOS['feliz'].extend('estou encantado|fiquei alegre|isso me alegra|que surpresa boa|me sinto feliz hoje|estou feliz por conseguir|estou empolgada|estou orgulhosa'.split('|'))
DADOS['neutro'].extend('estou tranquilo agora|quero aprender|estou tentando entender|estou trabalhando|vou sair|vou dormir|estou curioso para saber|como posso ajudar'.split('|'))
NEG = {'nao','nunca','nem'}
STOP = set('eu estou me com de do da o a os as e um uma hoje muito voce isso que sinto'.split())

def palavras(texto):
    texto = unicodedata.normalize('NFD', texto.lower())
    texto = ''.join(c for c in texto if not unicodedata.combining(c))
    return ''.join(c if 'a' <= c <= 'z' or '0' <= c <= '9' else ' ' for c in texto).split()[:48]

def features(texto):
    # As proximas 3 palavras recebem marca de negacao. 'mas' encerra o escopo.
    resultado=[]; restante=0; anterior=''
    for palavra in palavras(texto):
        palavra=palavra[:23]
        if palavra in NEG:
            restante=3; atual=palavra
        elif palavra == 'mas':
            restante=0; atual=palavra
        elif restante:
            atual='n_'+palavra; restante-=1
        else:
            atual=palavra
        if atual not in STOP: resultado.append(atual)
        if anterior: resultado.append(anterior+'_'+atual)
        anterior=atual
    return sorted(set(resultado))

def treinar():
    contagens=[]; vocab=set()
    for classe in CLASSES:
        c=collections.Counter()
        for frase in DADOS[classe]: c.update(features(frase))
        contagens.append(c); vocab.update(c)
    vocab=sorted(vocab)
    totais=[sum(c.values())+len(vocab) for c in contagens]
    pesos=[[math.log((c[f]+1)/total) for c,total in zip(contagens,totais)] for f in vocab]
    return vocab,pesos

VOCAB,PESOS=treinar()
INDICE={f:i for i,f in enumerate(VOCAB)}

def classificar(texto):
    indices=[INDICE[f] for f in features(texto) if f in INDICE]
    scores=[sum(PESOS[i][k] for i in indices) for k in range(4)]
    ordem=sorted(range(4),key=lambda k:scores[k],reverse=True)
    margem=scores[ordem[0]]-scores[ordem[1]]
    # Sem evidencia suficiente, nao atribui emocao.
    resultado=ordem[0] if indices and margem >= 0.8 else 3
    # Se palavras isoladas trazem evidencias fortes de emocoes diferentes,
    # preserva a expressao neutra em vez de escolher arbitrariamente.
    evidencias=set()
    for i in indices:
        if '_' in VOCAB[i]: continue
        ranking=sorted(range(4), key=lambda k:PESOS[i][k], reverse=True)
        if ranking[0] != 3 and PESOS[i][ranking[0]]-PESOS[i][ranking[1]] >= 0.8:
            evidencias.add(ranking[0])
    if len(evidencias)>1: resultado=3
    return CLASSES[resultado],round(margem,3),len(indices)

TESTES=[
 ('Hoje estou muito feliz!','feliz'),('Que alegria, deu certo!','feliz'),
 ('Estou contente com o resultado','feliz'),('Estou orgulhoso de voce','feliz'),
 ('Estou muito triste hoje','triste'),('Eu estou me sentindo sozinho','triste'),
 ('Sinto saudade de voce','triste'),('Estou desanimado hoje','triste'),
 ('Estou com raiva de tudo','raiva'),('Fiquei muito irritado','raiva'),
 ('Isso e injusto demais','raiva'),('Estou furioso com isso','raiva'),
 ('Ola, como voce esta?','neutro'),('Vou estudar agora','neutro'),
 ('Estou em casa hoje','neutro'),('xyzzy 987654','neutro'),
 ('Nao estou feliz hoje','neutro'),('Nao estou triste hoje','neutro'),
 ('Não estou com raiva hoje','neutro'),('Não estou muito feliz hoje','neutro'),
 ('','neutro'),('!!!','neutro'),('😀','neutro'),
 ('Estou feliz e triste','neutro'),
]

def exportar(destino):
    with open(destino,'w') as f:
        f.write('// Gerado por treinar_modelo.py. Naive Bayes multinomial, alpha=1.\n')
        f.write(f'constexpr int VOCAB_SIZE = {len(VOCAB)};\n')
        f.write('const char* const VOCAB[] = {\n')
        for palavra in VOCAB: f.write(json.dumps(palavra)+',\n')
        f.write('};\nconst float PESOS[][4] = {\n')
        for linha in PESOS: f.write('{'+','.join(f'{p:.7f}f' for p in linha)+'},\n')
        f.write('};\n')

if __name__ == '__main__':
    target = pathlib.Path(__file__).resolve().parents[1] / 'firmware/RoboOlhos/model.h'
    exportar(target)
    print(f'{sum(map(len,DADOS.values()))} exemplos, {len(VOCAB)} features -> {target.name}')
