.global main
.global _main
.text

main:
call _main
movq %rax, %rdi
movq $0x3C, %rax
syscall

_main:
  movl $3, %ebx
  movl %ebx, %eax
  addq $0, %rsp
  pop %rbp
  ret
