(module
(import "wasi_unstable" "fd_write" 
  (func $fd_write__4 (param $fd i32) (param $iovec i32) (param $iovec_len i32) (param $num_written i32) (result i32)))
(data (i32.const 65536) "\0e\00\00\00hello catac!\n")
(memory (export "memory") 2)
(func $main (export "_start") (local $stdout i32) (local $iovec i32) (local $written i32) 
  (i32.const 1) 
  (local.set $stdout) 
  (i32.const 0) 
  (local.set $iovec) 
  (local.get $iovec) 
  (i32.const 8) 
  i32.add 
  (local.set $written) 
  (local.get $iovec) 
  (i32.const 65536) 
  (call $string.store__2) 
  drop 
  (local.get $stdout) 
  (local.get $iovec) 
  (i32.const 1) 
  (local.get $written) 
  (call $fd_write__4) 
  drop 
 )

(func $string.store__2 (param $addr i32) (param $string i32) (result i32) 
  (local.get $addr) 
  (local.get $string) 
  (i32.const 4) 
  i32.add 
  i32.store 
  (local.get $addr) 
  (i32.const 4) 
  i32.add 
  (local.get $string) 
  i32.load 
  i32.store 
  (i32.const 0) 
 )


)
