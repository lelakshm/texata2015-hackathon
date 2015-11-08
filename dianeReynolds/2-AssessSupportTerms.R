library(boilerpipeR)
library(corpora)
library(RKEA)
library(RWeka)
library(skmeans)
library(SnowballC)
library(tau)
library(tm)
library(topicmodels)
library(wordcloud)
library(zipfR)
library(RTextTools)
library(dplyr)
library(cluster)
library(biclust)
library(ggplot2)
library(igraph)
library(fpc)


tdms <- removeSparseTerms(tdm, 0.8)
mtdms <- as.matrix(tdms)

freq <- rowSums(mtdms)
sorted <- sort(freq)
plot(sorted)

wf <- data.frame(word=names(sorted), freq=sorted)
head(wf)

#p <- ggplot(subset(subset(wf, freq>15), freq<20),aes(word,freq))
#p <- p + geom_bar(stat="identity")
#p <- p + theme(axis.text.x=element_text(angle=45,hjust=1))


