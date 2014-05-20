#include "stdio.h"
#include "stdlib.h"

int main(int argc, char** argv)
{
  int i;

  printf("%d arguments\n", argc);
  printf("argc address: %d\n", &argc);
  printf("argv address: %d\n", &argv);
  printf("local var i address: %d\n", &i);

  char * fmt = "arg %d: %s\n";
  printf("address of fmt: %d\n", &fmt);
  printf("address stored in fmt: %d\n", fmt);
  if (argc > 0) {
    printf("address stored in argv[0]: %dn", argv);
    printf("string in argv[0]: %sn", argv[0]);
  }
  if (argc > 1) {
    printf("address stored in argv[1]: %dn", argv+1);
    printf("string in argv[1]: %sn", argv[1]);
  }
  
  for (i=0; i<argc; i++)
    printf(fmt, i, argv[i]);

  return 0;
}
