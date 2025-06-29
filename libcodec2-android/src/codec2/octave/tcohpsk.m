% tcohpsk.m
% David Rowe Oct 2014
%
% Octave coherent PSK modem script that hs two modes:
%
% i) tests the C port of the coherent PSK modem.  This script loads
%    the output of unittest/tcohpsk.c and compares it to the output of
%    the reference versions of the same modem written in Octave.
%

% (ii) Runs the Octave version of the cohpsk modem to tune and develop
%      it, including extensive channel simulations such as AWGN noise,
%      fading/HF, frequency offset, frequency drift, and tx/rx sample
%      rate differences.

%  TODO:
%
%  [X] Test
%      [X] AWGN channel
%      [X] freq offset
%      [X] fading channel
%      [X] freq drift
%      [X] timing drift
%  [X] tune perf/impl loss to get closer to ideal
%      [X] linear interp of phase for better fading perf
%  [X] freq offset/drift feedback loop 
%  [X] PAPR measurement and reduction
%  [X] false sync
%      [X] doesn't sync up on noise (used EsNo = -12)
%      [X] similar but invalid signal like huge f off
%  [X] ability to "unsync" when signal dissapears
%  [ ] some calibrated tests against FreeDV 1600
%      + compare sound quality at various Es/Nos
%  [ ] sync
%      + set some req & implement
%      [ ] way to handle eom w/o nasties
%          + like mute ouput when signal has gone or v low snr
%          + instantaneous snr
%  [X] ssb tx filter with 3dB passband ripple
%      + diverisity helped for AWGN BER 0.024 down to 0.016
%      + Only a small change in fading perf with filter on/off
%      + however other filters may have other effects, should test this, 
%        e.g. scatter plots, some sort of BER metric?
%  [X] EsNo estimation
%  [ ] filter reqd with compression?
%      + make sure not too much noise passed into noise floor
%  [X] different diversity combination
%      + taking largest symbol didn't help
%  [X] histogram of bit errors
%      + lot of data
%      + ssb filter
%      + compression
%      + make sure it's flat with many errors

pkg load signal;
more off;

global passes = 0;
global fails = 0;

cohpsk_dev;
fdmdv_common;
autotest;

rand('state',1); 
randn('state',1);

% select which test  ----------------------------------------------------------

test = 'compare to c';
%test = 'awgn';
%test = 'fading';

% some parameters that can be over ridden, e.g. to disable parts of modem

initial_sync = 0;  % setting this to 1 put us straight into sync w/o freq offset est
ftrack_en    = 1;  % set to 1 to enable freq tracking
ssb_tx_filt  = 0;  % set to 1 to to simulate SSB tx filter with passband ripple
Fs           = 7500;

% predefined tests ....

if strcmp(test, 'compare to c')
  frames = 30;
  foff =  58.7;
  dfoff = -0.5/Fs;
  EsNodB = 8;
  fading_en = 0;
  hf_delay_ms = 2;
  compare_with_c = 1;
  sample_rate_ppm = -1500;
  ssb_tx_filt  = 0;
end

% should be BER around 0.015 to 0.02

if strcmp(test, 'awgn')
  frames = 100;
  foff =  58.7;
  dfoff = -0.5/Fs;
  EsNodB = 8;
  fading_en = 0;
  hf_delay_ms = 2;
  compare_with_c = 0;
  sample_rate_ppm = 0;
end

% Similar to AWGN - should be BER around 0.015 to 0.02

if strcmp(test, 'fading');
  frames = 100;
  foff = -25;
  dfoff = 0.5/Fs;
  EsNodB = 12;
  fading_en = 1;
  hf_delay_ms = 2;
  compare_with_c = 0;
  sample_rate_ppm = 0;
end

EsNo = 10^(EsNodB/10);

% modem constants ----------------------------------------------------------

Rs = 75;               % symbol rate in Hz
Nc = 7;                % number of carriers
Nd = 2;                % diveristy factor
framesize = 56;        % number of payload data bits in the frame

Nsw = 4;               % frames we demod for initial sync window
afdmdv.Nsym = 6;       % size of tx/tx root nyquist filter in symbols
afdmdv.Nt = 5;         % number of symbols we estimate timing over

clip = 6.5;            % Clipping of tx signal to reduce PAPR. Adjust by 
                       % experiment as Nc and Nd change.  Check out no noise 
                       % scatter diagram and AWGN/fading BER perf
                       % at operating points

% FDMDV init ---------------------------------------------------------------

afdmdv.Fs = Fs;
afdmdv.Nc = Nd*Nc-1;
afdmdv.Rs = Rs;

if Fs/afdmdv.Rs != floor(Fs/afdmdv.Rs)
  printf("\n  Oops, Fs/Rs must be an integer!\n\n");
  return
end

M = afdmdv.M  = afdmdv.Fs/afdmdv.Rs;
afdmdv.Nfilter = afdmdv.Nsym*M;
afdmdv.tx_filter_memory = zeros(afdmdv.Nc+1, afdmdv.Nfilter);
excess_bw = 0.5;
afdmdv.gt_alpha5_root = gen_rn_coeffs(excess_bw, 1/Fs, Rs, afdmdv.Nsym, afdmdv.M);

Fcentre = afdmdv.Fcentre = 1500;
afdmdv.Fsep = afdmdv.Rs*(1+excess_bw);
afdmdv.phase_tx = ones(afdmdv.Nc+1,1);

% non linear carrier spacing, combined with clip, helps PAPR a lot!

freq_hz = afdmdv.Fsep*( -Nc*Nd/2 - 0.5 + (1:Nc*Nd).^0.98 );
afdmdv.freq_pol = 2*pi*freq_hz/Fs;
afdmdv.freq = exp(j*afdmdv.freq_pol);
afdmdv.Fcentre = 1500;

afdmdv.fbb_rect = exp(j*2*pi*Fcentre/Fs);
afdmdv.fbb_phase_tx = 1;
afdmdv.fbb_phase_rx = 1;

afdmdv.Nrxdec = 31;
afdmdv.rxdec_coeff = fir1(afdmdv.Nrxdec-1, 0.25)';
afdmdv.rxdec_lpf_mem = zeros(1,afdmdv.Nrxdec-1+afdmdv.M);

P = afdmdv.P = 4;
afdmdv.phase_rx = ones(afdmdv.Nc+1,1);
afdmdv.Nfilter = afdmdv.Nsym*afdmdv.M;
afdmdv.rx_fdm_mem = zeros(1,afdmdv.Nfilter + afdmdv.M);
Q = afdmdv.Q = afdmdv.M/4;
if Q != floor(Q)
  printf("\n  Yeah .... if (Fs/Rs)/4 = M/4 isn't an integer we will just go and break things.\n\n");
end

afdmdv.rx_filter_mem_timing = zeros(afdmdv.Nc+1, afdmdv.Nt*afdmdv.P);
afdmdv.Nfiltertiming = afdmdv.M + afdmdv.Nfilter + afdmdv.M;

afdmdv.rx_filter_memory = zeros(afdmdv.Nc+1, afdmdv.Nfilter);

afdmdv.filt = 0;
afdmdv.prev_rx_symb = ones(1,afdmdv.Nc+1);

% COHPSK Init --------------------------------------------------------

acohpsk = standard_init();
acohpsk.framesize        = framesize;
acohpsk.ldpc_code        = 0;
acohpsk.ldpc_code_rate   = 1;
acohpsk.Nc               = Nc;
acohpsk.Rs               = Rs;
acohpsk.Ns               = 4;
acohpsk.coh_en           = 1;
acohpsk.Nd               = Nd;
acohpsk.modulation       = 'qpsk';
acohpsk.do_write_pilot_file = 1;      % enable this to dump pilot symbols to C .h file, e.g. if frame params change
acohpsk = symbol_rate_init(acohpsk);
acohpsk.Ndft = 1024;
acohpsk.f_est = afdmdv.Fcentre;

ch_fdm_frame_buf = zeros(1, Nsw*acohpsk.Nsymbrowpilot*afdmdv.M);

% -----------------------------------------------------------

tx_bits_log = [];
tx_symb_log = [];
rx_amp_log = [];
rx_phi_log = [];
ch_symb_log = [];
rx_symb_log = [];
rx_bits_log = [];
tx_bits_prev_log = [];
uvnoise_log = [];
nerr_log = [];
tx_baseband_log = [];
tx_fdm_frame_log = [];
ch_fdm_frame_log = [];
rx_fdm_frame_bb_log = [];
rx_filt_log = [];
rx_fdm_filter_log = [];
rx_baseband_log = [];
rx_fdm_frame_log = [];
ct_symb_ff_log = [];
rx_timing_log = [];
ratio_log = [];
foff_log = [];
f_est_log = [];
sig_rms_log = [];
noise_rms_log = [];           
noise_rms_filt_log = [];

% Channel modeling and BER measurement ----------------------------------------

rand('state',1); 
tx_bits_coh = round(rand(1,framesize*10));
ptx_bits_coh = 1;

Nerrs = Tbits = 0;
prev_tx_bits = prev_tx_bits2 = [];
error_positions_hist = zeros(1,framesize);

phase_ch = 1;
sync = initial_sync;
acohpsk.f_est = Fcentre;
acohpsk.f_fine_est = 0;
acohpsk.ct = 4;
acohpsk.ftrack_en = ftrack_en;

[spread spread_2ms hf_gain] = init_hf_model(Fs, frames*acohpsk.Nsymbrowpilot*afdmdv.M);
hf_n = 1;
nhfdelay = floor(hf_delay_ms*Fs/1000);
ch_fdm_delay = zeros(1, acohpsk.Nsymbrowpilot*M + nhfdelay);

% simulated SSB tx filter

[b, a] = cheby1(4, 3, [600, 2600]/(Fs/2));
[y filt_states] = filter(b,a,0);
h = freqz(b,a,(600:2600)/(Fs/(2*pi)));
filt_gain = (2600-600)/sum(abs(h) .^ 2);   % ensures power after filter == before filter

noise_rms_filt = 0;

% main loop --------------------------------------------------------------------

% run mod and channel as aseparate loop so we can resample to simulate sample rate differences

for f=1:frames
  tx_bits = tx_bits_coh(ptx_bits_coh:ptx_bits_coh+framesize-1);
  ptx_bits_coh += framesize;
  if ptx_bits_coh > length(tx_bits_coh)
    ptx_bits_coh = 1;
  end

  tx_bits_log = [tx_bits_log tx_bits];

  [tx_symb tx_bits] = bits_to_qpsk_symbols(acohpsk, tx_bits, []);
  tx_symb_log = [tx_symb_log; tx_symb];
  
  tx_fdm_frame = [];
  for r=1:acohpsk.Nsymbrowpilot
    tx_onesymb = tx_symb(r,:);
    [tx_baseband afdmdv] = tx_filter(afdmdv, tx_onesymb);
    tx_baseband_log = [tx_baseband_log tx_baseband];
    [tx_fdm afdmdv] = fdm_upconvert(afdmdv, tx_baseband);
    tx_fdm_frame = [tx_fdm_frame tx_fdm];
  end

  % clipping, which along with non-linear carrier spacing, improves PAPR
  % The value of clip is a function of Nc and is adjusted experimentally
  % such that the BER hit over no clipping at Es/No=8dB is small.

  ind = find(abs(tx_fdm_frame) > clip);
  tx_fdm_frame(ind) = clip*exp(j*angle(tx_fdm_frame(ind)));

  tx_fdm_frame_log = [tx_fdm_frame_log tx_fdm_frame];

  %
  % Channel --------------------------------------------------------------------
  %

  % simulate tx SSB filter with ripple

  if ssb_tx_filt
    [tx_fdm_frame filt_states] = filter(b,a,sqrt(filt_gain)*tx_fdm_frame, filt_states);
  end

  % frequency offset and frequency drift

  ch_fdm_frame = zeros(1,acohpsk.Nsymbrowpilot*M);
  for i=1:acohpsk.Nsymbrowpilot*M
    foff_rect = exp(j*2*pi*foff/Fs);
    foff += dfoff;
    phase_ch *= foff_rect;
    ch_fdm_frame(i) = tx_fdm_frame(i) * phase_ch;
  end
  foff_log = [foff_log foff];
  phase_ch /= abs(phase_ch);

  % optional fading

  if fading_en
    ch_fdm_delay(1:nhfdelay) = ch_fdm_delay(acohpsk.Nsymbrowpilot*M+1:nhfdelay+acohpsk.Nsymbrowpilot*M);
    ch_fdm_delay(nhfdelay+1:nhfdelay+acohpsk.Nsymbrowpilot*M) = ch_fdm_frame;

    for i=1:acohpsk.Nsymbrowpilot*M
      ahf_model = hf_gain*(spread(hf_n)*ch_fdm_frame(i) + spread_2ms(hf_n)*ch_fdm_delay(i));
      ch_fdm_frame(i) = ahf_model;
      hf_n++;
    end
  end

  % each carrier has power = 2, total power 2Nc, total symbol rate NcRs, noise BW B=Fs
  % Es/No = (C/Rs)/(N/B), N = var = 2NcFs/NcRs(Es/No) = 2Fs/Rs(Es/No)

  variance = 2*Fs/(acohpsk.Rs*EsNo);
  uvnoise = sqrt(0.5)*(randn(1,acohpsk.Nsymbrowpilot*M) + j*randn(1,acohpsk.Nsymbrowpilot*M));
  uvnoise_log = [uvnoise_log uvnoise];
  noise = sqrt(variance)*uvnoise;

  ch_fdm_frame += noise;

  ch_fdm_frame_log = [ch_fdm_frame_log ch_fdm_frame];
end

% simulate difference in sample clocks

tin=1;
tout=1;
ch_fdm_frame_log_out = zeros(1,length(ch_fdm_frame_log));
while tin < length(ch_fdm_frame_log)
      t1 = floor(tin);
      t2 = ceil(tin);
      f = tin - t1;
      ch_fdm_frame_log_out(tout) = (1-f)*ch_fdm_frame_log(t1) + f*ch_fdm_frame_log(t2);
      tout += 1;
      tin  += 1+sample_rate_ppm/1E6;
end
ch_fdm_frame_log = ch_fdm_frame_log_out(1:tout-1);

% Now run demod ----------------------------------------------------------------

ch_fdm_frame_log_index = 1;
nin = M;
f = 0;
nin_frame = acohpsk.Nsymbrowpilot*M;

%while (ch_fdm_frame_log_index + acohpsk.Nsymbrowpilot*M+M/P) < length(ch_fdm_frame_log)
for f=1:frames;
  acohpsk.frame = f;

  ch_fdm_frame = ch_fdm_frame_log(ch_fdm_frame_log_index:ch_fdm_frame_log_index + nin_frame - 1);
  ch_fdm_frame_log_index += nin_frame;

  %
  % Demod ----------------------------------------------------------------------
  %

  % store two frames of received samples so we can rewind if we get a good candidate

  ch_fdm_frame_buf(1:Nsw*acohpsk.Nsymbrowpilot*M-nin_frame) = ch_fdm_frame_buf(nin_frame+1:Nsw*acohpsk.Nsymbrowpilot*M);
  ch_fdm_frame_buf(Nsw*acohpsk.Nsymbrowpilot*M-nin_frame+1:Nsw*acohpsk.Nsymbrowpilot*M) = ch_fdm_frame;

  next_sync = sync;

  % if out of sync do Initial Freq offset estimation over NSW frames to flush out memories

  if (sync == 0)

    % we can test +/- 20Hz, so we break this up into 3 tests to cover +/- 60Hz

    max_ratio = 0;
    for acohpsk.f_est = Fcentre-40:40:Fcentre+40
       
      printf("  [%d] acohpsk.f_est: %f +/- 20\n", f, acohpsk.f_est);

      % we are out of sync so reset f_est and process two frames to clean out memories

      [ch_symb rx_timing rx_filt rx_baseband afdmdv acohpsk.f_est] = rate_Fs_rx_processing(afdmdv, ch_fdm_frame_buf, acohpsk.f_est, Nsw*acohpsk.Nsymbrowpilot, nin, 0);
      rx_baseband_log = [rx_baseband_log rx_baseband];
      
      rx_filt_log = [rx_filt_log rx_filt];
      ch_symb_log = [ch_symb_log; ch_symb];
      rx_timing_log = [rx_timing_log rx_timing];

      for i=1:Nsw-1
        acohpsk.ct_symb_buf = update_ct_symb_buf(acohpsk.ct_symb_buf, ch_symb((i-1)*acohpsk.Nsymbrowpilot+1:i*acohpsk.Nsymbrowpilot,:), acohpsk.Nct_sym_buf, acohpsk.Nsymbrowpilot);
      end
      [anext_sync acohpsk] = frame_sync_fine_freq_est(acohpsk, ch_symb((Nsw-1)*acohpsk.Nsymbrowpilot+1:Nsw*acohpsk.Nsymbrowpilot,:), sync, next_sync);

      if anext_sync == 1
        %printf("  [%d] acohpsk.ratio: %f\n", f, acohpsk.ratio);
        if acohpsk.ratio > max_ratio
          max_ratio   = acohpsk.ratio;
          f_est       = acohpsk.f_est - acohpsk.f_fine_est;
          next_sync   = anext_sync;
        end
      end
    end

    if next_sync == 1

      % we've found a sync candidate!
      % re-process last two frames with adjusted f_est then check again

      acohpsk.f_est = f_est;

      printf("  [%d] trying sync and f_est: %f\n", f, acohpsk.f_est);

      [ch_symb rx_timing rx_filt rx_baseband afdmdv f_est] = rate_Fs_rx_processing(afdmdv, ch_fdm_frame_buf, acohpsk.f_est, Nsw*acohpsk.Nsymbrowpilot, nin, 0);
      rx_baseband_log = [rx_baseband_log rx_baseband];
      rx_filt_log = [rx_filt_log rx_filt];
      ch_symb_log = [ch_symb_log; ch_symb];
      rx_timing_log = [rx_timing_log rx_timing];

      for i=1:Nsw-1
        acohpsk.ct_symb_buf = update_ct_symb_buf(acohpsk.ct_symb_buf, ch_symb((i-1)*acohpsk.Nsymbrowpilot+1:i*acohpsk.Nsymbrowpilot,:), acohpsk.Nct_sym_buf, acohpsk.Nsymbrowpilot);
      end
      [next_sync acohpsk] = frame_sync_fine_freq_est(acohpsk, ch_symb((Nsw-1)*acohpsk.Nsymbrowpilot+1:Nsw*acohpsk.Nsymbrowpilot,:), sync, next_sync);
      if abs(acohpsk.f_fine_est) > 2
        printf("  [%d] Hmm %f is a bit big so back to coarse est ...\n", f, acohpsk.f_fine_est);
        next_sync = 0;
      end

      if acohpsk.ratio < 0.9
        next_sync = 0;
      end
      if next_sync == 1
        % OK we are in sync!
        % demodulate first frame (demod completed below)

        printf("  [%d] in sync! f_est: %f ratio: %f \n", f, f_est, acohpsk.ratio);
        acohpsk.ct_symb_ff_buf(1:acohpsk.Nsymbrowpilot+2,:) = acohpsk.ct_symb_buf(acohpsk.ct+1:acohpsk.ct+acohpsk.Nsymbrowpilot+2,:);
      end
    end  
  end

  % If in sync just do sample rate processing on latest frame

  if sync == 1
    [ch_symb rx_timing rx_filt rx_baseband afdmdv acohpsk.f_est] = rate_Fs_rx_processing(afdmdv, ch_fdm_frame, acohpsk.f_est, acohpsk.Nsymbrowpilot, nin, acohpsk.ftrack_en);
    [next_sync acohpsk] = frame_sync_fine_freq_est(acohpsk, ch_symb, sync, next_sync);

    acohpsk.ct_symb_ff_buf(1:2,:) = acohpsk.ct_symb_ff_buf(acohpsk.Nsymbrowpilot+1:acohpsk.Nsymbrowpilot+2,:);
    acohpsk.ct_symb_ff_buf(3:acohpsk.Nsymbrowpilot+2,:) = acohpsk.ct_symb_buf(acohpsk.ct+3:acohpsk.ct+acohpsk.Nsymbrowpilot+2,:);

    rx_baseband_log = [rx_baseband_log rx_baseband];
    rx_filt_log = [rx_filt_log rx_filt];
    ch_symb_log = [ch_symb_log; ch_symb];     
    rx_timing_log = [rx_timing_log rx_timing];
    f_est_log = [f_est_log acohpsk.f_est];
  end

  % if we are in sync complete demodulation with symbol rate processing

  if (next_sync == 1) || (sync == 1)
    [rx_symb rx_bits rx_symb_linear amp_ phi_ sig_rms noise_rms] = qpsk_symbols_to_bits(acohpsk, acohpsk.ct_symb_ff_buf);
    rx_symb_log = [rx_symb_log; rx_symb];
    rx_amp_log = [rx_amp_log; amp_];
    rx_phi_log = [rx_phi_log; phi_];
    rx_bits_log = [rx_bits_log rx_bits];
    tx_bits_prev_log = [tx_bits_prev_log prev_tx_bits2];
    ratio_log = [ratio_log acohpsk.ratio];
    ct_symb_ff_log = [ct_symb_ff_log; acohpsk.ct_symb_ff_buf(1:acohpsk.Nsymbrowpilot,:)];
    sig_rms_log = [sig_rms_log sig_rms];
    noise_rms_log = [noise_rms_log noise_rms];
    noise_rms_filt = 0.9*noise_rms_filt + 0.1*noise_rms;
    noise_rms_filt_log = [noise_rms_filt_log noise_rms_filt];

    % BER stats

    if f > 2
      error_positions = xor(tx_bits_log((f-3)*framesize+1:(f-2)*framesize), rx_bits);
      Nerrs  += sum(error_positions);
      nerr_log = [nerr_log sum(error_positions)];
      Tbits += length(error_positions);
      error_positions_hist += error_positions;
    end
    printf("\r  [%d]", f);
  end

  % reset BER stats if we lose sync

  if sync == 1
    %Nerrs = 0;
    %Tbits = 0;
    %nerr_log = [];
  end

  [sync acohpsk] = sync_state_machine(acohpsk, sync, next_sync);

  % work out how many samples we need for next time

  nin = M;
  if sync == 1
    if rx_timing(length(rx_timing)) > M/P
      nin = M + M/P;
    end
    if rx_timing(length(rx_timing)) < -M/P
      nin = M - M/P;
    end
  end
  nin_frame = (acohpsk.Nsymbrowpilot-1)*M + nin;

  prev_tx_bits2 = prev_tx_bits;
  prev_tx_bits = tx_bits;

end

ber = Nerrs/Tbits;
printf("\nOctave EsNodB: %4.1f ber..: %4.3f Nerrs..: %d Tbits..: %d\n", EsNodB, ber, Nerrs, Tbits);

if compare_with_c

  % Output vectors from C port ---------------------------------------------------

  load tcohpsk_out.txt

  % Determine bit error rate

  
  sz = length(rx_bits_log_c);
  Nerrs_c = sum(xor(tx_bits_log(1:sz-framesize), rx_bits_log_c(framesize+1:sz)));
  Tbits_c = length(tx_bits_prev_log);
  ber_c = Nerrs_c/Tbits_c;
  printf("C EsNodB.....: %4.1f ber_c: %4.3f Nerrs_c: %d Tbits_c: %d\n", EsNodB, ber_c, Nerrs_c, Tbits_c);
  
  stem_sig_and_error(1, 111, tx_bits_log_c, tx_bits_log - tx_bits_log_c, 'tx bits', [1 length(tx_bits_log) -1.5 1.5])
  
  stem_sig_and_error(2, 211, real(tx_symb_log_c), real(tx_symb_log - tx_symb_log_c), 'tx symb re', [1 length(tx_symb_log_c) -1.5 1.5])
  stem_sig_and_error(2, 212, imag(tx_symb_log_c), imag(tx_symb_log - tx_symb_log_c), 'tx symb im', [1 length(tx_symb_log_c) -1.5 1.5])

  stem_sig_and_error(3, 211, real(tx_fdm_frame_log_c), real(tx_fdm_frame_log - tx_fdm_frame_log_c), 'tx fdm frame re', [1 length(tx_fdm_frame_log) -10 10])
  stem_sig_and_error(3, 212, imag(tx_fdm_frame_log_c), imag(tx_fdm_frame_log - tx_fdm_frame_log_c), 'tx fdm frame im', [1 length(tx_fdm_frame_log) -10 10])
  stem_sig_and_error(4, 211, real(ch_fdm_frame_log_c), real(ch_fdm_frame_log - ch_fdm_frame_log_c), 'ch fdm frame re', [1 length(ch_fdm_frame_log) -10 10])
  stem_sig_and_error(4, 212, imag(ch_fdm_frame_log_c), imag(ch_fdm_frame_log - ch_fdm_frame_log_c), 'ch fdm frame im', [1 length(ch_fdm_frame_log) -10 10])

  c = 1;
  stem_sig_and_error(5, 211, real(rx_baseband_log_c(c,:)), real(rx_baseband_log(c,:) - rx_baseband_log_c(c,:)), 'rx baseband re', [1 length(rx_baseband_log) -10 10])
  stem_sig_and_error(5, 212, imag(rx_baseband_log_c(c,:)), imag(rx_baseband_log(c,:) - rx_baseband_log_c(c,:)), 'rx baseband im', [1 length(rx_baseband_log) -10 10])
  stem_sig_and_error(6, 211, real(rx_filt_log_c(c,:)), real(rx_filt_log(c,:) - rx_filt_log_c(c,:)), 'rx filt re', [1 length(rx_filt_log) -1 1])
  stem_sig_and_error(6, 212, imag(rx_filt_log_c(c,:)), imag(rx_filt_log(c,:) - rx_filt_log_c(c,:)), 'rx filt im', [1 length(rx_filt_log) -1 1])

  [n m] = size(ch_symb_log);
  stem_sig_and_error(7, 211, real(ch_symb_log_c), real(ch_symb_log - ch_symb_log_c), 'ch symb re', [1 n -1.5 1.5])
  stem_sig_and_error(7, 212, imag(ch_symb_log_c), imag(ch_symb_log - ch_symb_log_c), 'ch symb im', [1 n -1.5 1.5])

  [n m] = size(rx_symb_log);
  stem_sig_and_error(8, 211, rx_amp_log_c, rx_amp_log - rx_amp_log_c, 'Amp Est', [1 n -1.5 1.5])
  phi_log_diff = rx_phi_log - rx_phi_log_c;
  phi_log_diff(find(phi_log_diff > pi)) -= 2*pi;
  phi_log_diff(find(phi_log_diff < -pi)) += 2*pi;
  stem_sig_and_error(8, 212, rx_phi_log_c, phi_log_diff, 'Phase Est', [1 n -4 4])
  stem_sig_and_error(9, 211, real(rx_symb_log_c), real(rx_symb_log - rx_symb_log_c), 'rx symb re', [1 n -1.5 1.5])
  stem_sig_and_error(9, 212, imag(rx_symb_log_c), imag(rx_symb_log - rx_symb_log_c), 'rx symb im', [1 n -1.5 1.5])

  stem_sig_and_error(10, 111, rx_bits_log_c, rx_bits_log - rx_bits_log_c, 'rx bits', [1 length(rx_bits_log) -1.5 1.5])
  stem_sig_and_error(11, 111, f_est_log_c - Fcentre - foff, f_est_log - f_est_log_c, 'f est', [1 length(f_est_log) -5 5])
  stem_sig_and_error(12, 111, rx_timing_log_c, rx_timing_log_c - rx_timing_log, 'rx timing', [1 length(rx_timing_log) -M M])

  check(tx_bits_log, tx_bits_log_c, 'tx_bits');
  check(tx_symb_log, tx_symb_log_c, 'tx_symb');
  check(tx_fdm_frame_log, tx_fdm_frame_log_c, 'tx_fdm_frame',0.01);
  check(ch_fdm_frame_log, ch_fdm_frame_log_c, 'ch_fdm_frame',0.01);
  check(ch_symb_log, ch_symb_log_c, 'ch_symb',0.05);
  check(rx_amp_log, rx_amp_log_c, 'rx_amp_log',0.01);
  check(phi_log_diff, zeros(length(phi_log_diff), Nc*Nd), 'rx_phi_log',0.1);
  check(rx_symb_log, rx_symb_log_c, 'rx_symb',0.01);
  check(rx_timing_log, rx_timing_log_c, 'rx_timing',0.005);
  check(rx_bits_log, rx_bits_log_c, 'rx_bits');
  check(f_est_log, f_est_log_c, 'f_est');
  check(sig_rms_log, sig_rms_log_c, 'sig_rms');
  check(noise_rms_log, noise_rms_log_c, 'noise_rms');
  
  printf("\npasses: %d fails: %d\n", passes, fails);

else
  
  papr = max(tx_fdm_frame_log.*conj(tx_fdm_frame_log)) / mean(tx_fdm_frame_log.*conj(tx_fdm_frame_log));
  papr_dB = 10*log10(papr);
  printf("av tx pwr: %4.2f PAPR: %4.2f av rx pwr: %4.2f\n", var(tx_fdm_frame_log), papr_dB, var(ch_fdm_frame_log));

  % some other useful plots

  f = figure(1)
  clf
  subplot(211)
  plot(real(tx_fdm_frame_log))
  title('tx fdm real');
  subplot(212)
  plot(imag(tx_fdm_frame_log))
  title('tx fdm imag');

  f = figure(2)
  clf
  spec = 20*log10(abs(fft(tx_fdm_frame_log)));
  l = length(spec);
  plot((Fs/l)*(1:l), spec)
  axis([1 Fs/2 0 max(spec)]);
  title('tx spectrum');
  ylabel('Amplitude (dB)')
  xlabel('Frequency (Hz)')
  grid;

  f = figure(3)
  clf;
  % plot combined signals to show diversity gains
  combined = rx_symb_log(:,1:Nc);
  for d=2:Nd
    combined += rx_symb_log(:, (d-1)*Nc+1:d*Nc);
  end
  plot(combined*exp(j*pi/4)/sqrt(Nd),'+')
  title('Scatter');
  ymax = abs(max(max(combined)));
  axis([-ymax ymax -ymax ymax])

  f = figure(4)
  clf;
  subplot(211)
  plot(rx_phi_log)
  subplot(212)
  plot(rx_amp_log)

  f = figure(5)
  clf;
  subplot(211)
  plot(rx_timing_log)
  title('rx timing');
  subplot(212)
  stem(ratio_log)
  title('Sync ratio');

  f = figure(6)
  clf;
  subplot(211)
  stem(nerr_log)
  title('Bit Errors');
  subplot(212)
  plot(noise_rms_filt_log,'r', sig_rms_log,'g');
  title('Est rms signal and noise')

  f = figure(7);
  clf;
  subplot(211)
  plot(foff_log,';freq offset;');
  hold on;
  plot(f_est_log - Fcentre,'g;freq offset est;');
  hold off;
  title('freq offset');
  legend("boxoff");  
  subplot(212)
  plot(foff_log(1:length(f_est_log)) - f_est_log + Fcentre)
  title('freq offset estimation error');

  f = figure(8)
  clf
  h = freqz(b,a,Fs/2);
  plot(20*log10(abs(h)))
  axis([1 Fs/2 -20 0])
  grid
  title('SSB tx filter')

  f = figure(9)
  clf
  plot(error_positions_hist)    
  title('histogram of bit errors')                               

  
end


% function to write C header file of noise samples so C version gives
% extactly the same results

function write_noise_file(uvnoise_log)

  m = length(uvnoise_log);

  filename = sprintf("../unittest/noise_samples.h");
  f=fopen(filename,"wt");
  fprintf(f,"/* unit variance complex noise samples */\n\n");
  fprintf(f,"/* Generated by write_noise_file() Octave function */\n\n");
  fprintf(f,"COMP noise[]={\n");
  for r=1:m
    if r < m
      fprintf(f, "  {%f,%f},\n", real(uvnoise_log(r)), imag(uvnoise_log(r)));
    else
      fprintf(f, "  {%f,%f}\n};", real(uvnoise_log(r)), imag(uvnoise_log(r)));
    end
  end

  fclose(f);
endfunction


% function to write float fading samples for use by C programs

%function write_noise_file(raw_file_name, Fs, dopplerSpreadHz, len_samples)
%  spread = doppler_spread(dopplerSpreadHz, Fs, len_samples);
%  spread_2ms = doppler_spread(dopplerSpreadHz, Fs, len_samples);
%  hf_gain = 1.0/sqrt(var(spread)+var(spread_2ms));
%
%  % interleave real imag samples
%
%  inter = zeros(1,len_samples*4);
%  inter(1:4) = hf_gain;
%  for i=1:len_samples
%    inter(i*4+1) = real(spread(i));
%    inter(i*4+2) = imag(spread(i));
%    inter(i*4+3) = real(spread_2ms(i));
%    inter(i*4+4) = imag(spread_2ms(i));
%  end
%  f = fopen(raw_file_name,"wb");
%  fwrite(f, inter, "float32");
%  fclose(f);
%endfunction
