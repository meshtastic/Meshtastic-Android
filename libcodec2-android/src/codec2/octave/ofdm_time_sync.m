% ofdm_time_sync.m
% David Rowe June 2019
%
% Tests ofdm modem sync time, using real, off air files

function ofdm_time_sync(filename, Ntrials=10)

  time_to_sync = []; passes = fails = 0;
  for toffset=0:Ntrials-1
    atime = ofdm_ldpc_rx(filename, mode="700D", interleave_frames = 1, "", start_secs=toffset, len_secs=5);
    if atime != -1
      passes++;
      time_to_sync = [time_to_sync atime];
    else
       fails++;
    end
  end
  printf("pass: %d fails: %d mean: %3.2f var %3.2f\n", passes, fails, mean(time_to_sync), var(time_to_sync));
  figure(1); clf; plot(time_to_sync);
endfunction


