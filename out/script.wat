(module
(import "wasi_unstable" "fd_write" 
  (func $fd_write//4 (param $fd i32) (param $iovec i32) (param $iovec_len i32) (param $num_written i32) (result i32)))
(data (i32.const 65536) "\06\00\00\00one!\n")
(memory (export "memory") 2)
(func $main (export "_start") 
  (i32.const 1) 
  (i32.const 1) 
  i32.eq 
  if 
  (i32.const 65536) 
  (call $print//1) 
  else 
  end 
 )

(func $string.store//2 (param $iovec i32) (param $string i32) 
  (local.get $iovec) 
  (local.get $string) 
  (i32.const 4) 
  i32.add 
  i32.store 
  (local.get $iovec) 
  (i32.const 4) 
  i32.add 
  (local.get $string) 
  i32.load 
  i32.store 
 )

(func $print//1 (param $message i32) (local $iovec i32) (local $iovec_len i32) (local $written i32) 
  (i32.const 4096) 
  (local.set $iovec) 
  (i32.const 1) 
  (local.set $iovec_len) 
  (local.get $iovec) 
  (local.get $message) 
  (call $string.store//2) 
  (local.get $iovec) 
  (i32.const 8) 
  i32.add 
  (local.set $written) 
  (i32.const 1) 
  (local.get $iovec) 
  (local.get $iovec_len) 
  (local.get $written) 
  (call $fd_write//4) 
  drop 
 )


)
