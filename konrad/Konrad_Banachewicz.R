## wd etc ####

require(stringr)
require(tm)
require(wordcloud)
require(RColorBrewer)
require(caret)
require(ggplot2)

## Q2: summarization of conversations ####
# files converted to txt (xml stuff purged) with parse_data.py

## build a corpus on processed files
# grab list of files
xlist <- dir("./data/defects", full.names = T)
# memory constraints: drop some files
# definitely the empty ones, but - for speedup NOW - also all smaller than 1kb
# 1kb cutoff is arbitrary but it reduces the problem size by a third

xsizes <- sapply(xlist, function(s) file.info(s)$size )
xsource <- DirSource("./data/defects", encoding = "UTF-8", pattern = "txt")
# nasty hack to tm properties - but no other automated way to filter which files are read
keep_files <- which(xsizes > 1000)
xsource$length <- length(keep_files)
xsource$filelist <- xsource$filelist[keep_files]
xcorpus <- VCorpus(xsource,
                   readerControl = list(language = "eng"))


## transformations
stopword_list <- stopwords("english")
# tolower
xcorpus <- tm_map(xcorpus, content_transformer(tolower))
# stopwords
xcorpus <- tm_map(xcorpus, removeWords, stopword_list)
# paranoid people live longer
# save.image("./results/corpus.RData")
# strip whitespaces
xcorpus <- tm_map(xcorpus, stripWhitespace)
# remove numbers
xcorpus <- tm_map(xcorpus, removeNumbers)
# save.image("./results/processed_corpus.RData")
# stemming - OPTIONAL
xcorpus <- tm_map(xcorpus, stemDocument)


# wordcloud on complete corpus
tdm <- TermDocumentMatrix(xcorpus)
m <- as.matrix(tdm)
v <- sort(rowSums(m),decreasing=TRUE)
d <- data.frame(word = names(v),freq=v)
pal <- brewer.pal(9, "BuGn")
pal <- pal[-(1:2)]
png("./results/wordcloud1.png", width=1280,height=800)
wordcloud(d$word,d$freq, scale=c(8,.3),min.freq=2,max.words=100, random.order=T, rot.per=.15, colors=pal, vfont=c("sans serif","plain"))
dev.off()

# wordcloud on a single corpus document
doclist <- c(1,23,456,7890)
for (which_document in doclist)
{
  xloc <- xcorpus[which_document]
  tdm <- TermDocumentMatrix(xloc)
  m <- as.matrix(tdm)
  m <- m[,1,drop = F]
  m <- m[rowSums(m)  != 0,1,drop = F]
  v <- sort(rowSums(m),decreasing=TRUE)
  d <- data.frame(word = names(v),freq=v)
  pal <- brewer.pal(9, "BuGn")
  pal <- pal[-(1:2)]
  png(paste("./results/wordcloud",which_document,".png", sep = ""), width=1280,height=800)
  wordcloud(d$word,d$freq, scale=c(8,.3),min.freq=2,max.words=100, random.order=T, rot.per=.15, colors=pal, vfont=c("sans serif","plain"))
  dev.off()
}

## Q3: clustering ####
# create a document-term matrix
dtm <- DocumentTermMatrix(xcorpus,
                           control = list(weighting =
                                            function(x)
                                              weightTfIdf(x, normalize =
                                                            FALSE),
                                          stopwords = TRUE))
# trim to manageable size
dtm <- removeSparseTerms(dtm, 0.95)
# convert to matrix
m <- as.matrix(dtm)
# standardize pre-transformation
prp0 <- preProcess(m, method = c("scale"))
m <- predict(prp0, m)

# rf proximity (unsupervised mode)
# subsampled for memory reasons => the bigger proportion of the sample used, the better
rf0 <- randomForest(x = m[1:2000,], do.trace = T, ntree = 50)
# multidimensional scaling
cmd0 <- cmdscale(rf0$proximity, k = 4)
# kmeans on proximity matrix
km0 <- kmeans(cmd0, centers = 5 )
# visualize the clusters
xmat <- data.frame(cmd0[,1:2], km0$cluster)
colnames(xmat) <- c("cmd1", "cmd2", "cluster")
p0 <-  ggplot(xmat) + geom_point(aes(x = cmd1, y = cmd2, col = factor(cluster)))

## Q4: recommendation #### 
xmat0 <- xmat
# read the list of user emails + discussions they participated in
user_list <- read_csv("./results/users0.txt")
user_list <- unique(user_list)
colnames(user_list)[1] <- "filename"

# attach file names in xmat
xmat$filename <- rownames(xmat)
rownames(xmat) <- NULL
xmat <- xmat[,3:4]

# simulated workaround
xmat$filename <- sample(user_list$filename, 2000, replace = T)

# attach cluster 
xmerge <- merge(user_list, xmat, all.x =T)
# and drop the file
xmerge <- unique(xmerge[,-1])

# loop over users, produce a list of most similar discussions for each
users_own_cluster <- list()
for (user in user_list$` user `)
{
  xloc <- xmerge[xmerge$` user ` == user,]
  # which clusters do the conversations of this user belong to
  cluster_loc <- unique(xloc$cluster)
  # 
  users_own_cluster[[user]] <- cluster_loc
  
}
