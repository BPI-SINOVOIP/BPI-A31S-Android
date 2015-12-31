#
# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import sys
from color import Coloring
from command import Command
from progress import Progress




class Remote(Command):
  common = True
  helpSummary = "View current topic branches"
  helpUsage = """
%prog add <remotebranchname> <url> [<project>...]
       %prog rm  <remotebranchname> [<project>...]


--------------



"""
  def str_last(self ,string):
     for c in string:
         last=c
     return last

  def Execute(self, opt, args):
   
    if not args:
      print >>sys.stderr, "error:..........wrong command........"
      print >>sys.stderr, "Usage:repo remote add <remotebranchname> <url> [<project>...]"
      print >>sys.stderr, "      repo remote rm  <remotebranchname> [<project>...] "                         
      print >>sys.stderr, "................................"
      return

    err = []
    operate=args[0]
    #url = args[2]
   # branch_name=args[1]
    if operate == "rm":
       if not len(args) >=2:
         print >>sys.stderr, "error:miss remotebrancname"
         return

       branch_name=args[1]
       projects = args[2:]
    elif operate == "add":
       if not len(args) >=3:
         print >>sys.stderr, "error:miss remotebranchname or url"
         return

       branch_name=args[1]
       projects = args[3:]
    else:
       print >>sys.stderr, "error: the operand is add or rm "
       return

   
       
    all = self.GetProjects(projects)
   # print >>sys.stderr, all
    pm = Progress('remote %s' % operate, len(all))
    for project in all:
       if operate == "add":
          if self.str_last(args[2])=="/":
             url = args[2]+project.name+'.git'
          else :
             url = args[2]+'/'+project.name+'.git'
       else:
         url = ""

       pm.update() 
       if not project.Remote(operate,branch_name,url):
         err.append(project)
    pm.end()
       
    if err:
      if len(err) == len(all):
        print >>sys.stderr, 'error: no project remote  %s %s' % (operate,branch_name)  
      else:
        for p in err:
          print >>sys.stderr,\
            "error: %s/: cannot remote %s %s " \
            % (p.relpath, operate,branch_name)
      sys.exit(1)
