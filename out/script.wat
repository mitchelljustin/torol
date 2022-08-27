(module
(import "wasi_unstable" "fd_write" 
  (func $fd_write//4 (param $fd i32) (param $iovec i32) (param $iovec_len i32) (param $num_written i32) (result i32)))
(data (i32.const 65536) "\10\00\00\00_ hello catac!\n")
(data (i32.const 65556) "\02\00\00\00hi")
(memory (export "memory") 2)
(func $main (export "_start") (local $message i32) (local $ptr i32) (local $i i32) 
  (i32.const 65536) 
  (local.set $message) 
  (local.get $message) 
  (i32.const 4) 
  i32.add 
  (local.set $ptr) 
  (i32.const 0) 
  (local.set $i) 
  loop $loop 
  (local.get $ptr) 
  (i32.const 48) 
  (local.get $i) 
  i32.add 
  i32.store8 
  (local.get $message) 
  (call $print//1) 
  (local.get $i) 
  (i32.const 1) 
  i32.add 
  (local.set $i) 
  (local.get $i) 
  (i32.const 9) 
  (i32.const 1) 
  i32.add 
  i32.lt_s 
  (br_if $loop) 
  end 
  (i32.const 0) 
  (local.set $i) 
  loop $loop 
  (i32.const 65556) 
  (call $print//1) 
  (local.get $i) 
  (i32.const 1) 
  i32.add 
  (local.set $i) 
  (local.get $i) 
  (i32.const 4) 
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
