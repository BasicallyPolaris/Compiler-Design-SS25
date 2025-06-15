
--

mov 1 a

loop_head:
    mov 10 check2
    if a < check then loop_body else loop_end
loob_body
    add a 1
    jmp loop_head
loop_end
    .......
    (return a)
