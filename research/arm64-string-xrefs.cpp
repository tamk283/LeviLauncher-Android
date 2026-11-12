#include <cstdint>
#include <cstring>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <unordered_set>
#include <string>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
int main(int argc,char**argv){
 if(argc!=6)return 2;
 int fd=open(argv[1],O_RDONLY);if(fd<0)return 3;
 struct stat st;if(fstat(fd,&st))return 4;
 auto p=(const unsigned char*)mmap(nullptr,st.st_size,PROT_READ,MAP_PRIVATE,fd,0);if(p==MAP_FAILED)return 5;
 uint64_t offset=strtoull(argv[2],nullptr,10),base=strtoull(argv[3],nullptr,10),size=strtoull(argv[4],nullptr,10);
 if(offset>(uint64_t)st.st_size||size>(uint64_t)st.st_size-offset)return 6;
 std::unordered_set<uint64_t> targets;std::ifstream input(argv[5]);std::string s;
 while(input>>s)targets.insert(strtoull(s.c_str(),nullptr,0));
 auto word=[&](uint64_t at){uint32_t w;std::memcpy(&w,p+offset+at,4);return w;};
 unsigned count=0;
 for(uint64_t i=0;i+4<=size;i+=4){
  uint32_t w=word(i);if((w&0x9f000000u)!=0x90000000u)continue;
  unsigned reg=w&31;if(reg==31)continue;
  int64_t imm=((w>>5)&0x7ffffu)*4+((w>>29)&3u);if(imm&(1<<20))imm-=(1<<21);
  uint64_t page=((base+i)&~uint64_t(4095))+imm*4096;
  for(unsigned gap=1;gap<=4&&i+gap*4+4<=size;gap++){
   uint32_t next=word(i+gap*4);
   if((next&0xff000000u)==0x91000000u&&((next>>5)&31)==reg){
    uint64_t add=(next>>10)&4095;if((next>>22)&1)add<<=12;
    uint64_t target=page+add;
    if(targets.count(target)){std::cout<<"{\"pc\":"<<(base+i)<<",\"target\":"<<target<<",\"gap\":"<<gap<<"}\n";if(++count>=2000)goto done;}
   }
   // Stop at unconditional branches, returns, or a second ADRP overwriting the tracked register.
   if((next&0xfc000000u)==0x14000000u||(next&0xfffffc1fu)==0xd65f0000u||((next&0x9f000000u)==0x90000000u&&(next&31)==reg))break;
  }
 }
 done:munmap((void*)p,st.st_size);close(fd);return 0;
}
