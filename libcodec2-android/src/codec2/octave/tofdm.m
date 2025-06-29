% tofdm.m
% David Rowe and Steve Sampson June 2017
%
% Octave script for comparing Octave and C versions of OFDM modem
%
% If running from the Octave command line a good idea to clear globals before
% each run:
%
%   octave> clear; tofdm;

% ------------------------------------------------------------------

Nframes = 10;
sample_clock_offset_ppm = 100;
foff_hz = 0.5;

more off; format;
ofdm_lib;
autotest;
ldpc
global passes = 0;
global fails = 0;

init_cml()
cml_support = 1

% ---------------------------------------------------------------------
% Run Octave version 
% ---------------------------------------------------------------------

% useful to test the modem at other Nc's, but if Nc != 17 we aren't set up for
% LDPC testing so disable
if getenv("NC")
  Nc = str2num(getenv("NC"));
  cml_support = 0;
else
  Nc = 17;
end
printf("Nc = %d LDPC testing: %d\n", Nc, cml_support);

config = ofdm_init_mode("700D");
config.Nc = Nc;
states = ofdm_init(config);
states.verbose = 0;
ofdm_load_const;

printf("Nbitsperframe: %d\n", Nbitsperframe);

if cml_support
  Nuwtxtsymbolsperframe = (states.Nuwbits+states.Ntxtbits)/bps;
  S_matrix = [1, j, -j, -1];
  EsNo = 10;
  symbol_likelihood_log = bit_likelihood_log = detected_data_log = [];

  % Set up LDPC code

  mod_order = 4; bps = 2; modulation = 'QPSK'; mapping = 'gray';
  demod_type = 0; decoder_type = 0; max_iterations = 100;

  load HRA_112_112.txt
  [code_param framesize rate] = ldpc_init_user(HRA_112_112, modulation, mod_order, mapping);
  assert(Nbitsperframe == (code_param.coded_bits_per_frame + states.Nuwbits + states.Ntxtbits));
end

tx_bits = zeros(1,Nbitsperframe);
rand('seed',1);

payload_data_bits = round(rand(1,(Nbitsperframe-Nuwbits-Ntxtbits)/2));
states.mean_amp = 1;  % start this with something sensible otherwise LDPC decode fails
if cml_support
  ibits = payload_data_bits;
  codeword = LdpcEncode(ibits, code_param.H_rows, code_param.P_matrix);
  tx_bits(Nuwbits+Ntxtbits+1:end) = codeword;
  tx_bits(1:Nuwbits+Ntxtbits) = [states.tx_uw zeros(1,Ntxtbits)];
else
  tx_bits = create_ldpc_test_frame(states, coded_frame=0);
end

% Run tx loop

tx_bits_log = []; tx_log = [];
for f=1:Nframes
  tx_bits_log = [tx_bits_log tx_bits];
  tx_log = [tx_log ofdm_mod(states, tx_bits)];
end

% Channel simulation ----------------------------------------------

rx_log = sample_clock_offset(tx_log, sample_clock_offset_ppm);
rx_log = freq_shift(rx_log, foff_hz, Fs);

% Rx ---------------------------------------------------------------

% Init rx with ideal timing so we can test with timing estimation disabled

Nsam = length(rx_log);
prx = 1;
nin = Nsamperframe+2*(M+Ncp);
states.rxbuf(Nrxbuf-nin+1:Nrxbuf) = rx_log(prx:nin);
prx += nin;

rxbuf_log = []; rxbuf_in_log = []; rx_sym_log = []; foff_hz_log = []; 
timing_est_log = timing_valid_log = timing_mx_log = [];
coarse_foff_est_hz_log = []; sample_point_log = [];
phase_est_pilot_log = []; rx_amp_log = [];
rx_np_log = []; rx_bits_log = [];
snr_log = []; mean_amp_log = [];

states.timing_en = 1;
states.foff_est_en = 1;
states.phase_est_en = 1;

if states.timing_en == 0
  % manually set ideal timing instant
  states.sample_point = Ncp;
end


for f=1:Nframes

  % insert samples at end of buffer, set to zero if no samples
  % available to disable phase estimation on future pilots on last
  % frame of simulation
 
  nin = states.nin;
  lnew = min(Nsam-prx+1,nin);
  rxbuf_in = zeros(1,nin);
  %printf("nin: %d prx: %d lnew: %d\n", nin, prx, lnew);
  if lnew
    rxbuf_in(1:lnew) = rx_log(prx:prx+lnew-1);
  end
  prx += lnew;

  [states rx_bits achannel_est_pilot_log arx_np arx_amp] = ofdm_demod(states, rxbuf_in);
  
  % log some states for comparison to C

  rxbuf_in_log = [rxbuf_in_log rxbuf_in];
  rxbuf_log = [rxbuf_log states.rxbuf];
  rx_sym_log = [rx_sym_log; states.rx_sym];
  phase_est_pilot_log = [phase_est_pilot_log; angle(achannel_est_pilot_log)];
  rx_amp_log = [rx_amp_log arx_amp];
  foff_hz_log = [foff_hz_log; states.foff_est_hz];
  timing_est_log = [timing_est_log; states.timing_est];
  timing_valid_log = [timing_valid_log; states.timing_valid];
  timing_mx_log = [timing_mx_log; states.timing_mx];
  coarse_foff_est_hz_log = [coarse_foff_est_hz_log; states.coarse_foff_est_hz];
  sample_point_log = [sample_point_log; states.sample_point];
  rx_np_log = [rx_np_log arx_np];
  rx_bits_log = [rx_bits_log rx_bits];
  mean_amp_log = [mean_amp_log; states.mean_amp];
  EsNo_estdB = esno_est_calc(arx_np);
  SNR_estdB = snr_from_esno(states, EsNo_estdB);
  snr_log = [snr_log; SNR_estdB];
  
  % Optional testing of LDPC functions

  if cml_support
    mean_amp = states.mean_amp;
    %mean_amp = 1;
    symbol_likelihood = Demod2D(arx_np(Nuwtxtsymbolsperframe+1:end)/mean_amp, S_matrix, EsNo, arx_amp(Nuwtxtsymbolsperframe+1:end)/mean_amp);
    bit_likelihood = Somap(symbol_likelihood);

    [x_hat paritychecks] = MpDecode(-bit_likelihood(1:code_param.coded_bits_per_frame), code_param.H_rows, code_param.H_cols, max_iterations, decoder_type, 1, 1);
    [mx mx_ind] = max(paritychecks);
    detected_data = x_hat(mx_ind,:);
    
    % make sure LDPC decoding is working OK
    
    % assert(codeword == detected_data);
    
    [m n] = size(symbol_likelihood);
    symbol_likelihood_log = [symbol_likelihood_log; reshape(symbol_likelihood,m*n,1)];
    bit_likelihood_log = [bit_likelihood_log; bit_likelihood'];
    detected_data_log = [detected_data_log detected_data];
  end
  
end

% ---------------------------------------------------------------------
% Run C version and plot Octave and C states and differences 
% ---------------------------------------------------------------------

printf("\nRunning C version....\n");

% Override default path by:
%   1. if running from octave CLI: setting path_to_tofdm = "/your/path/to/tofdm"
%   2. If running from shell....." set PATH_TO_OFDM = "/your/path/to/tofdm"

if exist("path_to_tofdm", "var") == 0
  path_to_tofdm = "../build_linux/unittest/tofdm"
end

if getenv("PATH_TO_TOFDM")
  path_to_tofdm = getenv("PATH_TO_TOFDM")
  printf("setting path from env var\n");
end

path_to_tofdm = sprintf("%s --nc %d", path_to_tofdm, Nc); % append Nc for variable Nc tests

if cml_support == 0
  path_to_tofdm = sprintf("%s --noldpc", path_to_tofdm);
end

system(path_to_tofdm);
load tofdm_out.txt;

fg = 1;

f = figure(fg++); clf; plot(rx_np_log,'+'); title('Octave Scatter Diagram'); axis([-1.5 1.5 -1.5 1.5]);
f = figure(fg++); clf; plot(rx_np_log_c,'+'); title('C Scatter Diagram'); axis([-1.5 1.5 -1.5 1.5]);

stem_sig_and_error(fg++, 111, tx_bits_log_c, tx_bits_log - tx_bits_log_c, 'tx bits', [1 length(tx_bits_log) -1.5 1.5])

stem_sig_and_error(fg, 211, real(tx_log_c), real(tx_log - tx_log_c), 'tx re', [1 length(tx_log_c) -0.1 0.1])
stem_sig_and_error(fg++, 212, imag(tx_log_c), imag(tx_log - tx_log_c), 'tx im', [1 length(tx_log_c) -0.1 0.1])

stem_sig_and_error(fg, 211, real(rx_log_c), real(rx_log - rx_log_c), 'rx re', [1 length(rx_log_c) -0.1 0.1])
stem_sig_and_error(fg++, 212, imag(rx_log_c), imag(rx_log - rx_log_c), 'rx im', [1 length(rx_log_c) -0.1 0.1])

stem_sig_and_error(fg, 211, real(rxbuf_in_log_c), real(rxbuf_in_log - rxbuf_in_log_c), 'rxbuf in re', [1 length(rxbuf_in_log_c) -0.1 0.1])
stem_sig_and_error(fg++, 212, imag(rxbuf_in_log_c), imag(rxbuf_in_log - rxbuf_in_log_c), 'rxbuf in im', [1 length(rxbuf_in_log_c) -0.1 0.1])

stem_sig_and_error(fg, 211, real(rxbuf_log_c), real(rxbuf_log - rxbuf_log_c), 'rxbuf re', [1 length(rxbuf_log_c) -0.1 0.1])
stem_sig_and_error(fg++, 212, imag(rxbuf_log_c), imag(rxbuf_log - rxbuf_log_c), 'rxbuf im', [1 length(rxbuf_log_c) -0.1 0.1])

stem_sig_and_error(fg, 211, real(rx_sym_log_c), real(rx_sym_log - rx_sym_log_c), 'rx sym re', [1 length(rx_sym_log_c) -1.5 1.5])
stem_sig_and_error(fg++, 212, imag(rx_sym_log_c), imag(rx_sym_log - rx_sym_log_c), 'rx sym im', [1 length(rx_sym_log_c) -1.5 1.5])

% for angles pi and -pi are the same

d = phase_est_pilot_log - phase_est_pilot_log_c; d = angle(exp(j*d));

stem_sig_and_error(fg, 211, phase_est_pilot_log_c, d, 'phase est pilot', [1 length(phase_est_pilot_log_c) -1.5 1.5])
stem_sig_and_error(fg++, 212, rx_amp_log_c, rx_amp_log - rx_amp_log_c, 'rx amp', [1 length(rx_amp_log_c) -1.5 1.5])

stem_sig_and_error(fg  , 211, foff_hz_log_c, (foff_hz_log - foff_hz_log_c), 'foff hz', [1 length(foff_hz_log_c) -1.5 1.5])

stem_sig_and_error(fg++, 212, timing_mx_log_c, (timing_mx_log - timing_mx_log_c), 'timing mx', [1 length(timing_mx_log_c) 0 2])

stem_sig_and_error(fg,   211, timing_est_log_c, (timing_est_log - timing_est_log_c), 'timing est', [1 length(timing_est_log_c) -1.5 1.5])
stem_sig_and_error(fg++, 212, sample_point_log_c, (sample_point_log - sample_point_log_c), 'sample point', [1 length(sample_point_log_c) -1.5 1.5])

stem_sig_and_error(fg++, 111, rx_bits_log_c, rx_bits_log - rx_bits_log_c, 'rx bits', [1 length(rx_bits_log) -1.5 1.5])

% Run through checklist -----------------------------

check(states.rate_fs_pilot_samples, pilot_samples_c, 'pilot_samples');
check(tx_bits_log, tx_bits_log_c, 'tx_bits');
check(tx_log, tx_log_c, 'tx');
check(rx_log, rx_log_c, 'rx');
check(rxbuf_in_log, rxbuf_in_log_c, 'rxbuf in');
check(rxbuf_log, rxbuf_log_c, 'rxbuf');
check(rx_sym_log, rx_sym_log_c, 'rx_sym', tol=10E-3);
check(phase_est_pilot_log, phase_est_pilot_log_c, 'phase_est_pilot', tol=1E-2, its_an_angle=1);
check(rx_amp_log, rx_amp_log_c, 'rx_amp');
check(timing_est_log, timing_est_log_c, 'timing_est');
check(timing_valid_log, timing_valid_log_c, 'timing_valid');
check(timing_mx_log, timing_mx_log_c, 'timing_mx');
check(coarse_foff_est_hz_log, coarse_foff_est_hz_log_c, 'coarse_foff_est_hz');
check(sample_point_log, sample_point_log_c, 'sample_point');
check(foff_hz_log, foff_hz_log_c, 'foff_est_hz');
check(rx_bits_log, rx_bits_log_c, 'rx_bits');
if cml_support
  check(symbol_likelihood_log, symbol_likelihood_log_c, 'symbol_likelihood_log', tol=1E-2);
  check(bit_likelihood_log, bit_likelihood_log_c, 'bit_likelihood_log');
  check(detected_data_log, detected_data_log_c, 'detected_data');
end
check(mean_amp_log, mean_amp_log_c, 'mean_amp_log');
check(snr_log, snr_log_c, 'snr_log');
printf("\npasses: %d fails: %d\n", passes, fails);

