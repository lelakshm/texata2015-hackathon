install.packages("tm")
library(tm)


cor <- Corpus(VectorSource(data.cdets.fr$Description))

## corpus cleaning
cor.cleaned <- tm_map(cor, content_transformer(tolower))
cor.cleaned <- tm_map(cor.cleaned, removeNumbers)
cor.cleaned <- tm_map(cor.cleaned, removePunctuation)
cor.cleaned <- tm_map(cor.cleaned, removeWords, stopwords("english"))
cor.cleaned <- tm_map(cor.cleaned, stripWhitespace)
cor.cleaned <- tm_map(cor.cleaned, stemDocument)

## document term matrix
cor.dtm <- DocumentTermMatrix(cor.cleaned)
cor.dtm <- removeSparseTerms(cor.dtm , 0.5)

## computing TF-IDF
dtm.tfidf <- weightTfIdf(cor.dtm)

## kmeans clustering

tfidf.matrix <- as.matrix(dtm.tfidf)

normEucl <- function(mtrx){
  mtrx / apply(mtrx, 1, function(x) sum(x^2)^.5)
}

tfidf.matrix.norm <- normEucl(tfidf.matrix)

nclusters <- 2
nTopWords <- 10
maxIter <- 10

###Clustering
cluster.res <- kmeans(tfidf.matrix.norm, 
                      centers = nclusters,
                      iter.max = maxIter)


cl.df <- data.frame(cluster.res$centers)


cluster.res.desc <- data.frame()
rowIndx <- 1

for(i in 1:nclusters){
  tmpTerms <- cl.df[i, head(order(cl.df[i, ], decreasing = TRUE), nTopWords)]
  
  for(j in 1:nTopWords){
    cluster.res.desc[rowIndx, "cl_id"] <- i
    cluster.res.desc[rowIndx, "term"] <- names(tmpTerms)[j]
    cluster.res.desc[rowIndx, "freq"] <- tmpTerms[1, j]
    rowIndx <- rowIndx + 1
  }
}

#adding cluster number to initial dataset

for(i in 1:nrow(data.cdets.fr)){
  data.cdets.fr[i, "desc.clusters"] <- cluster.res$cluster[i]
}

# stemCompletion( 
#   c("vike", "includ"),
#   dictionary = c("include", "manage", "viking"),
#   type = "first")

#write clustering result into files
write.csv(data.cdets.fr, file = "data_cdets.csv")
write.csv(cluster.res.desc, file = "data_cdets_cl_res.csv")

