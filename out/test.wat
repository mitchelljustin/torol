(module
(memory (export "memory") 2)
(data (i32.const 65536) "hello")
(func $main (export "_start") 
(i32.const 65536)
(i32.const 5)
(i32.const 1)
(call $print)
)
)
