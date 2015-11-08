
library(tm)


#Clustering functions

normEucl <- function(mtrx){
  mtrx / apply(mtrx, 1, function(x) sum(x^2)^.5)
}

getTFIDFMatrix <- function (text.vector) {
  cor <- Corpus(VectorSource(text.vector))
  
  ## corpus cleaning
  cor <- tm_map(cor, content_transformer(tolower))
  cor <- tm_map(cor, removeNumbers)
  cor <- tm_map(cor, removePunctuation)
  cor <- tm_map(cor, removeWords, stopwords("english"))
  cor <- tm_map(cor, stripWhitespace)
  cor <- tm_map(cor, stemDocument)
  
  ## document term matrix
  cor.dtm <- DocumentTermMatrix(cor)
  cor.dtm <- removeSparseTerms(cor.dtm , 0.1)
  
  ## computing TF-IDF
  dtm.tfidf <- weightTfIdf(cor.dtm)
  
  ## kmeans clustering
  
  tfidf.matrix <- as.matrix(dtm.tfidf)
  tfidf.matrix <- normEucl(tfidf.matrix)
  
  tfidf.matrix
}

getCleanCorpus <-  function (text.vector) {
  cor <- Corpus(VectorSource(text.vector))
  
  ## corpus cleaning
  cor <- tm_map(cor, content_transformer(tolower))
  cor <- tm_map(cor, removeNumbers)
  cor <- tm_map(cor, removePunctuation)
  cor <- tm_map(cor, removeWords, stopwords("english"))
  cor <- tm_map(cor, stripWhitespace)
  #cor <- tm_map(cor, stemDocument)

}
getCleanCorpus2 <-  function (text.vector) {
  cor <- Corpus(VectorSource(text.vector))
  
  ## corpus cleaning
  cor <- tm_map(cor, content_transformer(tolower))
  cor <- tm_map(cor, removeNumbers)
  cor <- tm_map(cor, removePunctuation)
  cor <- tm_map(cor, removeWords, stopwords("english"))
  cor <- tm_map(cor, stripWhitespace)
  cor <- tm_map(cor, stemDocument)
  
}


getDTM <- function (cor) {
  ## document term matrix
  cor.dtm <- DocumentTermMatrix(cor)
  cor.dtm
}

getTFIDF <- function(cor.dtm){
  dtm.tfidf <- weightTfIdf(cor.dtm)
  
  tfidf.matrix <- as.matrix(dtm.tfidf)
  tfidf.matrix <- normEucl(tfidf.matrix)
  
  tfidf.matrix
}
  
  
getClusterDescDF <- function (cluster.centers.matrix, nClusters, nTopWords) {
  cl.df <- data.frame(cluster.centers.matrix)
  
  cluster.res.desc <- data.frame()
  rowIndx <- 1
  
  for(i in 1:nClusters){
    tmpTerms <- cl.df[i, head(order(cl.df[i, ], decreasing = TRUE), nTopWords)]
    
    for(j in 1:nTopWords){
      cluster.res.desc[rowIndx, "cl_id"] <- i
      cluster.res.desc[rowIndx, "term"] <- names(tmpTerms)[j]
      cluster.res.desc[rowIndx, "freq"] <- tmpTerms[1, j]
      rowIndx <- rowIndx + 1
    }
  }
  cluster.res.desc
}


addClusterIdColumn <- function (data, colname, indx.seq, cluster.res) {
  j <- 1
  for(i in indx.seq){
    data[i, colname] <- cluster.res$cluster[j]
    j <- j + 1
  }
  data
}



#######################################
# PRIMARY GROUP 
#######################################
group1.nClusters <- 10
group1.res.colname <- "headline_cl_id"
maxIter <- 10


text.vector <- data.cdets.fr$Headline

tfidf.matrix <- getTFIDFMatrix(text.vector)


###Clustering
cluster.res <- kmeans(tfidf.matrix, 
                      centers = group1.nClusters,
                      iter.max = maxIter)


##Clusters description
cluster.res.desc <- getClusterDescDF(cluster.res$centers, group1.nClusters, 10)


#adding cluster id to initial dataset

data.cdets.fr <- addClusterIdColumn(data.cdets.fr, 
                                    group1.res.colname, 
                                    1:nrow(data.cdets.fr),
                                    cluster.res)
                                    

##########################
#clustering second step  
##########################
group2.src.colname <- "Description"
group2.nClusters <- 5
group2.res.colname <- "description_cl_id"
maxIter <- 10
cluster.res.desc2 <- data.frame()
#i <- 1
for (i in 2: group1.nClusters){
  print(paste(as.character(i), " of ", as.character(group1.nClusters) ))
  text.vector<- data.cdets.fr[
    data.cdets.fr[,group1.res.colname] == i, group2.src.colname]
  
  #######################
#  tfidf.matrix <- getTFIDFMatrix(text.vector)
  #######################
  
  cor <- getCleanCorpus(text.vector)
  
  sentiment.scores <- getSentimentScoreFromCorpus(cor)
  
  dtm <- getDTM(cor)
  
  tfidf.matrix <- getTFIDF(dtm)

  ###Clustering
  cluster.res <- kmeans(tfidf.matrix, 
                        centers = group2.nClusters,
                        iter.max = maxIter)
  
  
  ##Clusters description
  tmp <- getClusterDescDF(cluster.res$centers, group2.nClusters, 10)
  tmp[, "pr_cl_id"] <- i
  cluster.res.desc2 <- rbind(cluster.res.desc2, tmp)
  
  #adding cluster id to initial dataset
  
  data.cdets.fr[as.numeric(rownames(data.cdets.fr[
    data.cdets.fr[,group1.res.colname] == i, ])), group2.res.colname ] <- cluster.res$cluster
  data.cdets.fr[as.numeric(rownames(data.cdets.fr[
    data.cdets.fr[,group1.res.colname] == i, ])), "sent_score" ] <- sentiment.scores
  
}


# write clustering result into files
write.csv(data.cdets.fr, file = "data_cdets.csv")
write.csv(cluster.res.desc, file = "data_cdets_cl_res.csv")
write.csv(cluster.res.desc2, file = "data_cdets_c2_res.csv")
