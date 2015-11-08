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

#tdmb = removeSparseTerms(tdm,0.9995)
#mtdmb = as.matrix(tdmb)

length(kfit$cluster[kfit$cluster==6])
tmp <- as.matrix(kfit$cluster[kfit$cluster==6])
row1 <- row.names(mtdmb) == row.names(tmp)[1]
row2 <- row.names(mtdmb) == row.names(tmp)[2]
row3 <- row.names(mtdmb) == row.names(tmp)[3]

rows = row1 + row2 + row3
print(sum(rows))
print(rows)

sub <- mtdmb[rows,]
docs <- mtdmb[,colSums(sub>0)]

str(docs)

frdocs <- rowSums(docs)
set.seed(4382)
wordcloud(names(frdocs), frdocs, min.freq=50)
wordcloud(names(frdocs), frdocs, 
          max.words=50, scale=c(5, .1), 
          colors=brewer.pal(6, "Dark2"))  


