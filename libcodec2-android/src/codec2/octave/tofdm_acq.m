% tofdm_acq.m
% Octave <-> C test for OFDM modem acquisition

ofdm_lib;
autotest;
randn('seed',1);
pkg load signal;
more off;

% generate a file of transmit samples
filename = "test_datac0.raw";
ofdm_tx(filename,"datac0",1,10,"awgn","bursts",1);

printf("\nRunning Octave version....\n");
config = ofdm_init_mode("datac0");
states = ofdm_init(config);  
states.verbose = 1; states.data_mode = "burst"; states.postambledetectoren = 1;
states.timing_mx_thresh = 0.15;

ofdm_load_const;
frx=fopen(filename,"rb");
nin = states.nin; rxbufst = states.rxbufst;
rx = fread(frx, nin, "short")/(states.amp_scale/2);
f = 0;
timing_mx_log = []; ct_est_log = []; foff_est_log = []; timing_valid_log = []; nin_log = [];

while(length(rx) == nin)
  printf(" %2d ",f++);
  [timing_valid states] = ofdm_sync_search(states, rx);
  timing_mx_log = [timing_mx_log states.timing_mx];
  ct_est_log = [ct_est_log states.ct_est];
  foff_est_log = [foff_est_log states.foff_est_hz];
  timing_valid_log = [timing_valid_log states.timing_valid];
  nin_log = [nin_log states.nin];
  
  % reset these to defaults, as they get modified when timing_valid asserted
  states.nin = nin;
  states.rxbufst = rxbufst;
  
  rx = fread(frx, nin, "short")/(states.amp_scale/2);
  printf("\n");
end   
fclose(frx);

printf("\nRunning C version....\n");
path_to_unittest = "../build_linux/unittest";
if getenv("PATH_TO_UNITTEST")
  path_to_unittest = getenv("PATH_TO_UNITTEST")
  printf("setting path from env var to %s\n", path_to_unittest);
end
system(sprintf("%s/tofdm_acq %s", path_to_unittest, filename));
load tofdm_acq_out.txt;

fg = 1; passes = 0; ntests = 0;

tx_preamble = states.tx_preamble;
stem_sig_and_error(fg, 211, real(tx_preamble_c), real(tx_preamble_c - tx_preamble), 'tx preamble re')
stem_sig_and_error(fg++, 212, imag(tx_preamble_c), imag(tx_preamble_c - tx_preamble), 'tx preamble im')
passes += check(tx_preamble, tx_preamble_c, 'tx preamble', 0.1); ntests++;
tx_postamble = states.tx_postamble;
stem_sig_and_error(fg, 211, real(tx_postamble_c), real(tx_postamble_c - tx_postamble), 'tx postamble re')
stem_sig_and_error(fg++, 212, imag(tx_postamble_c), imag(tx_postamble_c - tx_postamble), 'tx postamble im')
passes += check(tx_postamble, tx_postamble_c, 'tx postamble', 0.1); ntests++;

stem_sig_and_error(fg, 211, real(timing_mx_log_c), real(timing_mx_log_c - timing_mx_log), 'timing mx')
passes += check(timing_mx_log, timing_mx_log_c, 'timing_mx'); ntests++;
stem_sig_and_error(fg++, 212, real(ct_est_log_c), real(ct_est_log_c - ct_est_log), 'ct est')
passes += check(ct_est_log, ct_est_log_c, 'ct_est_mx'); ntests++;

stem_sig_and_error(fg, 211, real(foff_est_log_c), real(foff_est_log_c - foff_est_log), 'foff est')
passes += check(foff_est_log, foff_est_log_c, 'foff_est'); ntests++;
stem_sig_and_error(fg++, 212, real(timing_valid_log_c), real(timing_valid_log_c - timing_valid_log), 'timing valid')
passes += check(timing_valid_log, timing_valid_log_c, 'timing_valid'); ntests++;
passes += check(nin_log, nin_log_c, 'nin'); ntests++;

if passes == ntests printf("PASS\n"); else printf("FAIL\n"); end

  



