(module
(global $f (mut i32) (i32.const 0))
(global $g (mut i32) (i32.const 0))
(memory (export "memory") 2)
(func $main (export "_start") 
  (global.set $f) 
  (i32.const 1) 
  (global.set $g) 
 )


)
