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

# ***************************************************************************************
cleanCorpus <- function (corpus)
{
  tmp <- corpus
  tmp <- tm_map(tmp, content_transformer(tolower))

  #identify custom "stop words" and remove them now
  csw <- c("https","cdetsng.cisco.com","sapi","api","bugs","cscg","file",
           "diffs","commit","viking","gladiator","name","field","cs","csg","csb",
           "ws","audittrail","parent","history","txt","xml","note","comments",
           "on","by","nameupdatedon","nameupdatedby", "type")
  tmp <- tm_map(tmp, removeWords, csw)
  tmp <- tm_map(tmp, removeNumbers)
  tmp <- tm_map(tmp, removeWords, stopwords("english"))
  tmp <- tm_map(tmp, removePunctuation)
  tmp <- tm_map(tmp, stripWhitespace)
  tmp <- tm_map(tmp, stemDocument)
  csw2 <- c("repli","descript","statist","url","view",
            "avg","titl","problem","share","vote","can",
            "rate","thank","use","configur","interfac",
            "feb", "will", "need","see","know","also",
            "like", "run", "one", "help", "want", "wed",
            "address", "link", "cisco", "regard", "tue",
            "mon", "fri", "thu", "jan", "mar", "apr",
            "may","jun","jul","aug","sep","oct","nov","dec",
            "sat","just","follow","work","pleas","hello",
            "hope","differ","check","come","think","show",
            "way","correct","case","caus","sure","now","enabl",
            "tri","give","thing")
  tmp <- tm_map(tmp, removeWords, csw2)
  
  csw3 = c("actual","alreadi","anyon","appreci","detail",
           "either","fine","got","happen","howev","idea",
           "kind","let","might","month","much","realli",
           "right","seem","sinc","solut","someth","specif",
           "suggest","take","tell","well","welcome","year")
  tmp <- tm_map(tmp, removeWords, csw3)
  
  csw4 = c("ago","answer","context","possibl","provid",
           "question","still","two","version","chang","set")
  tmp <- tm_map(tmp, removeWords, csw4)
  
  return(tmp)
}

BigramTokenizer <-
  function(x)
    unlist(lapply(ngrams(words(x), 2), paste, collapse = " "), use.names = FALSE)

buildCorpus <- function(path)
{
  corpus <- Corpus(DirSource(directory=path, encoding="UTF-8"))
  corpus <- cleanCorpus(corpus)
}

buildtdm <- function(corpus)
{
  tdm <- TermDocumentMatrix(corpus)
  tdm <- removeSparseTerms(tdm, 0.25)
}

buildbigtdm <- function(corpus)
{
  bigtdm <- TermDocumentMatrix(corpus, control = list(tokenize = BigramTokenizer))
  bigtdm <- removeSparseTerms(bigtdm, 0.7)
}


# ***************************************************************************************
options(stringsAsFactors = FALSE)
corpora = c("CDETS","Support","TZ")
paths = c("./Hackathon-Texata-2015/Defects-ASR9k", 
          "./Hackathon-Texata-2015/SupportCommunity/RS/content", 
          "./Hackathon-Texata-2015/TechZone")

corpus <- buildCorpus(paths[2])

tdm <- TermDocumentMatrix(corpus)
#mtdm <- as.matrix(tdm)

