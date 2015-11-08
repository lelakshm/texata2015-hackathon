#install.packages("XML")

setwd("C:\\Users\\j.jefimovs\\Documents\\Private\\TEXATA 2015\\Finals\\rep")
require(XML)
#####################################################################################
# CDETS
#####################################################################################

file.path.basedir <- "./data/Unconfirmed 84888/Hackathon-Texata-2015/Defects-ASR9k/" 
file.list <- list.files(path = file.path.basedir)
sampleSize <- 1000;

data.cdets.fr <- data.frame()
file.indx <- 1  


for (file.indx in 1:sampleSize){
  file <- file.list[file.indx]
  data <- xmlParse(paste(file.path.basedir, "/", file,  sep = ""), isURL=FALSE)
  xml_data <- xmlToList(data)
  
  i <- 1
  while (names(xml_data$Defect[i]) ==  "Field") {
    colname <- xml_data$Defect[[i]]$.attrs[[1]]
    data.cdets.fr[file.indx, colname] <- xml_data$Defect[[i]]$text
    i <- i +1
  }

  print(paste(as.character(file.indx), " : ", as.character(sampleSize), " :  ", file ))
}

#####################################################################################
# SUPPORT FORUMS
#####################################################################################
data.forums.fr <- data.frame()
file.path.basedir <- "./data/Support-Forums" 
file.list <- list.files(path = file.path.basedir)

i <- 1
for (file in file.list){
  conn <- file(paste(file.path.basedir, "/", file, sep = ""), open = "r")
  lines <-readLines(conn)
  data.forums.fr[i, "id"] <- substr(file, 1, 7)
  data.forums.fr[i, "filename"] <- file
  data.forums.fr[i, "title"] <- lines[3]
  data.forums.fr[i, "url"] <- lines[6]
  data.forums.fr[i, "stats"] <- lines[8]
  data.forums.fr[i, "desc"] <- lines[11]
  
  reply.str <- ""
  if (length(lines) >= 14) {
    for (l in seq(14, length(lines), by = 3)){
      reply.str <- paste(reply.str , lines[l])
    }  
  }
  data.forums.fr[i, "reply"] <- reply.str
  i <- i + 1
  close(conn)
}

write.csv(data.forums.fr, file = "data_forums.csv")

#####################################################################################
# TZ
#####################################################################################

file.path.basedir <- "./data/TZ/files" 
file.list <- list.files(path = file.path.basedir)

data.tz.fr <- data.frame()
i <- 1  
for (file in file.list){
  data <- xmlParse(paste(file.path.basedir, "/", file,  sep = ""), isURL=FALSE)
  xml_data <- xmlToList(data)
  
  for(attr in names(xml_data)){
    data.tz.fr[i, attr] <- paste("",xml_data[[attr]], sep="")
  }
  
  i <- i  + 1
  print(i)
  print(file)
}

write.csv(data.tz.fr, file = "data_tz.csv")


#######################
#LOAD SENTIMENT WORDS
######################
baseDir <- getwd()
sentiment.pos = scan(file.path(baseDir, "data", 'sentiment', 'positive_words.txt'), what='character', comment.char=';')
sentiment.neg = scan(file.path(baseDir, "data", 'sentiment', 'negative_words.txt'), what='character', comment.char=';')



