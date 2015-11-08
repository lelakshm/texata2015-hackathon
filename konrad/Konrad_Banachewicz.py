# -*- coding: utf-8 -*-
"""
Created on Sun Nov  8 09:46:27 2015

@author: konrad
"""

projPath = "/Users/konrad/Documents/projects/texata-finals/"
import sys  
import re
from os import listdir
from os.path import isfile, join
import string

### defects files ##
#locpath = 'data/Hackathon-Texata-2015/Defects-ASR9k/'
## list the available files: 91058
#files_list = [ f for f in listdir(projPath + locpath) if isfile(join(projPath + locpath,f)) ]
## convert the cdets files .xml -> .txt (remove punctuation and html code)
## customize the punctuation list: 
## - keep components of file names (init_something.c) => don't drop . or _
## - replace some symbols with ' ' instead of excluding
#exclude = '!"#$%&\'()+,-:;<=>?@[\\]^`{|}~'
### list the available files
#files_list = [ f for f in listdir(projPath + locpath) if isfile(join(projPath + locpath,f)) ]
## walk through the files list 
#for f in files_list:
#    # input and output names
#    input_name = projPath + locpath+ f
#    output_name = projPath + 'data/defects/' + f[0:-4] + '.txt'
#    g = open(output_name,"w") 
#    # process file line by line
#    for line in open(input_name).readlines():
#        line = line.rstrip('\n').lstrip()
#        if (1 - line.startswith('<')):
#            # cleanup the line
#            line = line.replace('&gt','')
#            line = line.replace('&#xD','')
#            line = line.replace('&quot','')
#            line = line.replace('/', ' ')
#            line = line.replace('*', ' ')
#            # line = ''.join(ch for ch in line if ch not in exclude)
#            line = line.translate(string.maketrans("",""), exclude)
#            # print line
#            g.write(line)
#    g.close()
#    print output_name
    


## grab user names from CDETS files
locpath = 'data/Hackathon-Texata-2015/Defects-ASR9k/'
exclude = set(string.punctuation)
output_name = projPath + 'results/' + 'users_files.txt'
## list the available files
files_list = [ f for f in listdir(projPath + locpath) if isfile(join(projPath + locpath,f)) and f.endswith('xml') ]
g = open(output_name,"w")
g.write('file, user \n')
for f in files_list:
    # input and output names
    input_name = projPath + locpath+ f
    # process file line by line
    for line in open(input_name).readlines():
        line = line.rstrip('\n').lstrip()
        if 'wrote' in line:
            match = re.findall(r'[\w\.-]+@[\w\.-]+', line)
            if len(match):
                g.write(f + ',' +match[0] + '\n')
g.close()   
