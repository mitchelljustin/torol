(module
(import "wasi_unstable" "fd_write" 
  (func $fd_write//4 (param $fd i32) (param $iovec i32) (param $iovec_len i32) (param $num_written i32) (result i32)))
(global $memory (mut i32) (i32.const 0))
(global $i (mut i32) (i32.const 0))
(data (i32.const 65536) "\04\00\00\00OK\n")
(data (i32.const 65544) "\08\00\00\00not OK\n")
(memory (export "memory") 2)
(func $main (export "_start") 
  (i32.const 0) 
  (global.set $memory) 
  (i32.const 0) 
  (global.set $i) 
  loop $loop 
  (global.get $i) 
  (i32.const 3) 
  i32.lt_s 
  if 
  (i32.const 65536) 
  (call $print//1) 
  else 
  (i32.const 65544) 
  (call $print//1) 
  end 
  (global.get $i) 
  (i32.const 1) 
  i32.add 
  (global.set $i) 
  (global.get $i) 
  (i32.const 10) 
  i32.lt_s 
  (br_if $loop) 
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
  (i32.const 0) 
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
