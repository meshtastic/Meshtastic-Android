% fdmdv_ut.m
%
% Unit Test program for FDMDV modem.  Useful for general development as it has
% both tx and rx sides, and basic AWGN channel simulation.
%
% Copyright David Rowe 2012
% This program is distributed under the terms of the GNU General Public License 
% Version 2
%

fdmdv;               % load modem code
 
% Simulation Parameters --------------------------------------

frames = 100;
EbNo_dB = 6.3;
Foff_hz = -100;
modulation = 'dqpsk';
hpa_clip = 150;

% ------------------------------------------------------------

more off;
tx_filt = zeros(Nc,M);
rx_symbols_log = [];
rx_phase_log = 0;
rx_timing_log = 0;
tx_pwr = 0;
noise_pwr = 0;
rx_fdm_log = [];
rx_baseband_log = [];
rx_bits_offset = zeros(Nc*Nb*2);
prev_tx_symbols = ones(Nc+1,1); prev_tx_symbols(Nc+1) = 2;
prev_rx_symbols = ones(Nc+1,1);
ferr = 0;
foff = 0;
foff_log = [];
tx_baseband_log = [];
tx_fdm_log = [];

% BER stats

total_bit_errors = 0;
total_bits = 0;
bit_errors_log = [];
sync_bit_log = [];
test_frame_sync_log = [];
test_frame_sync_state = 0;

% SNR estimation states

sig_est = zeros(Nc+1,1);
noise_est = zeros(Nc+1,1);

% fixed delay simuation

Ndelay = M+20;
rx_fdm_delay = zeros(Ndelay,1);

% ---------------------------------------------------------------------
% Eb/No calculations.  We need to work out Eb/No for each FDM carrier.
% Total power is sum of power in all FDM carriers
% ---------------------------------------------------------------------

C = 1; % power of each FDM carrier (energy/sample).  Total Carrier power should = Nc*C = Nc
N = 1; % total noise power (energy/sample) of noise source across entire bandwidth

% Eb  = Carrier power * symbol time / (bits/symbol)
%     = C *(1/Rs) / Nb
Eb_dB = 10*log10(C) - 10*log10(Rs) - 10*log10(Nb);

No_dBHz = Eb_dB - EbNo_dB;

% Noise power = Noise spectral density * bandwidth
% Noise power = Noise spectral density * Fs/2 for real signals
N_dB = No_dBHz + 10*log10(Fs/2);
Ngain_dB = N_dB - 10*log10(N);
Ngain = 10^(Ngain_dB/20);

% C/No = Carrier Power/noise spectral density
%      = power per carrier*number of carriers / noise spectral density
CNo_dB = 10*log10(C)  + 10*log10(Nc) - No_dBHz;

% SNR in equivalent 3000 Hz SSB channel

B = 3000;
SNR = CNo_dB - 10*log10(B);

% freq offset simulation states

phase_offset = 1;
freq_offset = exp(j*2*pi*Foff_hz/Fs);
foff_phase = 1;
t = 0;
foff = 0;
fest_state = 0;
fest_timer = 0;
sync_mem = zeros(1,Nsync_mem);
sync = 0;
sync_log = [];

snr_log = [];

Nspec=1024;
spec_mem=zeros(1,Nspec);
SdB = zeros(1,Nspec);

% ---------------------------------------------------------------------
% Main loop 
% ---------------------------------------------------------------------

for f=1:frames

  % -------------------
  % Modulator
  % -------------------

  tx_bits = get_test_bits(Nc*Nb);
  tx_symbols = bits_to_psk(prev_tx_symbols, tx_bits, modulation);
  prev_tx_symbols = tx_symbols;
  tx_baseband = tx_filter(tx_symbols);
  tx_baseband_log = [tx_baseband_log tx_baseband];
  tx_fdm = fdm_upconvert(tx_baseband);
  tx_pwr = 0.9*tx_pwr + 0.1*real(tx_fdm)*real(tx_fdm)'/(M);

  % -------------------
  % Channel simulation
  % -------------------

  % frequency offset

  %Foff_hz += 1/Rs;
  Foff = Foff_hz;
  for i=1:M
    % Time varying freq offset
    %Foff = Foff_hz + 100*sin(t*2*pi/(300*Fs));
    %t++;
    freq_offset = exp(j*2*pi*Foff/Fs);
    phase_offset *= freq_offset;
    tx_fdm(i) = phase_offset*tx_fdm(i);
  end

  tx_fdm = real(tx_fdm);

  % HPA non-linearity

  tx_fdm(find(abs(tx_fdm) > hpa_clip)) = hpa_clip;
  tx_fdm_log = [tx_fdm_log tx_fdm];

  rx_fdm = tx_fdm;

  % AWGN noise

  noise = Ngain*randn(1,M);
  noise_pwr = 0.9*noise_pwr + 0.1*noise*noise'/M;
  rx_fdm += noise;
  rx_fdm_log = [rx_fdm_log rx_fdm];

  % update spectrum

  l=length(rx_fdm);
  spec_mem(1:Nspec-l) = spec_mem(l+1:Nspec);
  spec_mem(Nspec-l+1:Nspec) = rx_fdm;
  S=fft(spec_mem.*hanning(Nspec)',Nspec);
  SdB = 0.9*SdB + 0.1*20*log10(abs(S));

 
  % -------------------
  % Demodulator
  % -------------------

  % shift down to complex baseband

  for i=1:M
    fbb_phase_rx = fbb_phase_rx*fbb_rect';
    rx_fdm(i) = rx_fdm(i)*fbb_phase_rx;
  end
  mag = abs(fbb_phase_rx);
  fbb_phase_rx /= mag;

  % frequency offset estimation and correction, need to call rx_est_freq_offset even in sync
  % mode to keep states updated
  
  [pilot prev_pilot pilot_lut_index prev_pilot_lut_index] = get_pilot(pilot_lut_index, prev_pilot_lut_index, M);
  [foff_coarse S1 S2] = rx_est_freq_offset(rx_fdm, pilot, prev_pilot, M, !sync);

  if sync == 0
    foff = foff_coarse;
  end
  
  foff_log = [ foff_log foff ];
  foff_rect = exp(j*2*pi*foff/Fs);
  
  for i=1:M
    foff_phase *= foff_rect';
    rx_fdm(i) = rx_fdm(i)*foff_phase;
  end

  rx_fdm_filter = rxdec_filter(rx_fdm, M);
  rx_filt = down_convert_and_rx_filter(rx_fdm_filter, M, M/Q);

  [rx_symbols rx_timing] = rx_est_timing(rx_filt, M);
  rx_timing_log = [rx_timing_log rx_timing];

  %rx_phase = rx_est_phase(rx_symbols);
  %rx_phase_log = [rx_phase_log rx_phase];
  %rx_symbols = rx_symbols*exp(j*rx_phase);

  [rx_bits sync_bit foff_fine pd] = psk_to_bits(prev_rx_symbols, rx_symbols, modulation);
  if strcmp(modulation,'dqpsk')
    rx_symbols_log = [rx_symbols_log pd];
  else
    rx_symbols_log = [rx_symbols_log rx_symbols];
  endif
  foff -= 0.5*foff_fine;
  
  prev_rx_symbols = rx_symbols;
  sync_bit_log = [sync_bit_log sync_bit];
  
  % freq est state machine

  [sync reliable_sync_bit fest_state fest_timer sync_mem] = freq_state(sync_bit, fest_state, fest_timer, sync_mem);
  sync_log = [sync_log sync];

  % Update SNR est

  [sig_est noise_est] = snr_update(sig_est, noise_est, pd);
  snr_log = [snr_log calc_snr(sig_est, noise_est)];

  % count bit errors if we find a test frame
  % Allow 15 frames for filter memories to fill and time est to settle

  [test_frame_sync bit_errors] = put_test_bits(test_bits, rx_bits);
  
  if test_frame_sync == 1
    total_bit_errors = total_bit_errors + bit_errors;
    total_bits = total_bits + Ntest_bits;
    bit_errors_log = [bit_errors_log bit_errors];
    else
      bit_errors_log = [bit_errors_log 0];
  end
 
  % test frame sync state machine, just for more informative plots
    
  next_test_frame_sync_state = test_frame_sync_state;
  if (test_frame_sync_state == 0)
    if (test_frame_sync == 1)      
      next_test_frame_sync_state = 1;
      test_frame_count = 0;
    end
  end

  if (test_frame_sync_state == 1)
    % we only expect another test_frame_sync pulse every 4 symbols
    test_frame_count++;
    if (test_frame_count == 4)
      test_frame_count = 0;
      if ((test_frame_sync == 0))      
        next_test_frame_sync_state = 0;
      end
    end
  end
  test_frame_sync_state = next_test_frame_sync_state;
  test_frame_sync_log = [test_frame_sync_log test_frame_sync_state];
end

% ---------------------------------------------------------------------
% Print Stats
% ---------------------------------------------------------------------

ber = total_bit_errors / total_bits;

% Peak to Average Power Ratio calcs from http://www.dsplog.com

papr = max(tx_fdm_log.*conj(tx_fdm_log)) / mean(tx_fdm_log.*conj(tx_fdm_log));
papr_dB = 10*log10(papr);

% Note Eb/No set point is for Nc data carriers only, excluding pilot.
% This is convenient for testing BER versus Eb/No.  Measured SNR &
% Eb/No includes power of pilot.  Similar for SNR, first number is SNR
% excluding pilot pwr for Eb/No set point, 2nd value is measured SNR
% which will be a little higher as pilot power is included. Note current SNR
% est algorithm only works for QPSK, gives silly values for 8PSK.

printf("Bits/symbol.: %d\n", Nb);
printf("Num carriers: %d\n", Nc);
printf("Bit Rate....: %d bits/s\n", Rb);
printf("Eb/No (meas): %2.2f (%2.2f) dB\n", EbNo_dB, 10*log10(0.25*tx_pwr*Fs/(Rs*Nc*noise_pwr)));
printf("bits........: %d\n", total_bits);
printf("errors......: %d\n", total_bit_errors);
printf("BER.........: %1.4f\n",  ber);
printf("PAPR........: %1.2f dB\n", papr_dB);
printf("SNR...(meas): %2.2f (%2.2f) dB\n", SNR, calc_snr(sig_est, noise_est));

% ---------------------------------------------------------------------
% Plots
% ---------------------------------------------------------------------

figure(1)
clf;
[n m] = size(rx_symbols_log);
plot(real(rx_symbols_log(1:Nc+1,15:m)),imag(rx_symbols_log(1:Nc+1,15:m)),'+')
axis([-3 3 -3 3]);
title('Scatter Diagram');

figure(2)
clf;
subplot(211)
plot(rx_timing_log)
title('timing offset');
subplot(212)
plot(foff_log, '-;freq offset;')
hold on;
plot(sync_log*75, 'r;Sync State & course(0) fine(1) freq tracking;');
hold off;
title('Freq offset (Hz)');

figure(3)
clf;
subplot(211)
plot(real(tx_fdm_log));
title('FDM Tx Signal');
subplot(212)
plot((0:Nspec/2-1)*Fs/Nspec, SdB(1:Nspec/2) - 20*log10(Nspec/2))
axis([0 Fs/2 -40 0])
grid
title('FDM Rx Spectrum');

figure(4)
clf;
subplot(311)
stem(sync_bit_log)
axis([0 frames 0 1.5]);
title('BPSK Sync')
subplot(312)
stem(bit_errors_log);
title('Bit Errors for test frames')
subplot(313)
plot(test_frame_sync_log);
axis([0 frames 0 1.5]);
title('Test Frame Sync')

figure(5)
clf
subplot(211)
plot(snr_log)
subplot(212)
%plot(20*log10(sig_est(1:Nc))-20*log10(sig_est(Nc+1))+6)
%axis([1 Nc -6 6]);
sdB_pc = 20*log10(sig_est(1:Nc+1));
bar(sdB_pc(1:Nc) - mean(sdB_pc(1:Nc)))
axis([0 Nc+1 -3 3]);
