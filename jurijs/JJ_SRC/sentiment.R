getSentimentScore <- function(text.vector){
  cor <- Corpus(VectorSource(text.vector))
  
  ## corpus cleaning
  cor <- tm_map(cor, content_transformer(tolower))
  cor <- tm_map(cor, removeNumbers)
  cor <- tm_map(cor, removePunctuation)
  cor <- tm_map(cor, removeWords, stopwords("english"))
  cor <- tm_map(cor, stripWhitespace)
  #cor <- tm_map(cor, stemDocument)
  
  ## document term matrix
  cor.dtm <- DocumentTermMatrix(cor)
  #cor.dtm <- removeSparseTerms(cor.dtm , 0.5)
  
  pos.words.cnt = sum(!is.na(match(cor.dtm$dimnames$Terms, sentiment.pos)))
  neg.words.cnt = sum(!is.na(match(cor.dtm$dimnames$Terms, sentiment.neg)))
  
  score = pos.words.cnt - neg.words.cnt  
  
  score
}

getSentimentScoreFromCorpus <- function(cor){
  res <- c()
  for (i in 1:length(cor)){
    res[i] <- getSentimentScore(cor$content[[i]][[1]]) 
  }
  
  #res[is.na(res)] <- 0 
  res
}