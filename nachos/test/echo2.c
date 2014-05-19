#include "stdio.h"
#include "stdlib.h"

int main(int argc, char** argv)
{
  int i;

  printf("%d arguments\n", argc);
  printf("argc address: %X\n", &argc);
  printf("argv address: %X\n", &argv);
  
  for (i=0; i<argc; i++)
    printf("arg %d: %s\n", i, argv[i]);

  return 0;
}
