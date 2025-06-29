% fdmdv_demod.m
%
% Demodulator function for FDMDV modem (Octave version).  Requires
% 8kHz sample rate raw files as input
%
% Copyright David Rowe 2012
% This program is distributed under the terms of the GNU General Public License 
% Version 2
%

function fdmdv_demod(rawfilename, nbits, NumCarriers=14, errorpatternfilename, symbolfilename)

  fdmdv; % include modem code
  f = fdmdv_init(NumCarriers);
  Nc = f.Nc; Nb = f.Nb; Rs = f.Rs; M = f.M; Fs = f.Fs; Nsync_mem = f.Nsync_mem;
  test_bits = f.test_bits; Q = f.Q; P = f.P;

  modulation = 'dqpsk';

  fin = fopen(rawfilename, "rb");
  gain = 1000;
  frames = nbits/(Nc*Nb);

  prev_rx_symbols = ones(Nc+1,1);
  foff_phase_rect = 1;

  % BER stats

  total_bit_errors = 0;
  total_bits = 0;
  bit_errors_log = [];
  sync_log = [];
  test_frame_sync_log = [];
  test_frame_sync_state = 0;
  error_pattern_log = [];

  % SNR states

  sig_est = zeros(Nc+1,1);
  noise_est = zeros(Nc+1,1);

  % logs of various states for plotting

  rx_symbols_log = [];
  rx_timing_log = [];
  foff_coarse_log = [];
  foff_log = [];
  rx_fdm_log = [];
  snr_est_log = [];

  % misc states

  nin = M;                 % timing correction for sample rate differences
  foff = 0;

  fest_state = 0;
  fest_timer = 0;
  sync_mem = zeros(1,Nsync_mem);
  sync = 0;
  sync_log = [];

  % spectrum states

  Nspec=1024;
  spec_mem=zeros(1,Nspec);
  SdB = zeros(1,Nspec);

  % optionally save output symbols 

  if nargin == 5
    fm = fopen(symbolfilename,"wb");
    dual_rx_symbols = zeros(1, 2*Nc);
    dual_rx_bits = zeros(1,2*Nc*Nb);
  end

  atimer = 0;
  
  % Main loop ----------------------------------------------------

  for fr=1:frames
    
    % obtain nin samples of the test input signal
    
    for i=1:nin
      rx_fdm(i) = fread(fin, 1, "short")/gain;
    end
    
    rx_fdm_log = [rx_fdm_log rx_fdm(1:nin)];

    % update spectrum

    l=length(rx_fdm);
    spec_mem(1:Nspec-l) = spec_mem(l+1:Nspec);
    spec_mem(Nspec-l+1:Nspec) = rx_fdm;
    S=fft(spec_mem.*hanning(Nspec)',Nspec);
    SdB = 0.9*SdB + 0.1*20*log10(abs(S));

    % shift down to complex baseband

    for i=1:nin
      f.fbb_phase_rx = f.fbb_phase_rx*f.fbb_rect';
      rx_fdm(i) = rx_fdm(i)*f.fbb_phase_rx;
    end
    mag = abs(f.fbb_phase_rx);
    f.fbb_phase_rx /= mag;

    % frequency offset estimation and correction

    [pilot prev_pilot f.pilot_lut_index f.prev_pilot_lut_index] = get_pilot(f, f.pilot_lut_index, f.prev_pilot_lut_index, nin);
    [foff_coarse S1 S2 f] = rx_est_freq_offset(f, rx_fdm, pilot, prev_pilot, nin, !sync );
    
    if sync == 0
      foff = foff_coarse;
    end
    foff_coarse_log = [foff_coarse_log foff_coarse];

    foff_rect = exp(j*2*pi*foff/Fs);

    for i=1:nin
      foff_phase_rect *= foff_rect';
      rx_fdm_fcorr(i) = rx_fdm(i)*foff_phase_rect;
    end

    % baseband processing

    if 0
      % easier to understand, but more memory and CPU hungry filtering and down conversion

      [rx_baseband f] = fdm_downconvert(f, rx_fdm_fcorr, nin);
      [rx_filt f] = rx_filter(f, rx_baseband, nin);
    else
      % more efficient filtering and down conversion
    
      [rx_fdm_filter f] = rxdec_filter(f, rx_fdm_fcorr, nin);
      [rx_filt f] = down_convert_and_rx_filter(f, rx_fdm_filter, nin, M/Q);
    end

    [rx_symbols rx_timing env f] = rx_est_timing(f, rx_filt, nin);
    rx_timing_log = [rx_timing_log rx_timing];

    nin = M;    
    if rx_timing > M/P
      nin += M/P;
    end
    if rx_timing < -M/P;
      nin -= M/P;
    end
    %printf("fr: %d rx_timing: %d nin = %d\n", fr, rx_timing, nin);
    
    rx_symbols_log = [rx_symbols_log rx_symbols.*conj(prev_rx_symbols./abs(prev_rx_symbols))*exp(j*pi/4)];
    [rx_bits sync_bit f_err pd] = psk_to_bits(f, prev_rx_symbols, rx_symbols, modulation);

    % optionally save output symbols 

    if (nargin == 5)

      % this free runs, and is reset by an "entered sync" state

      if (sync_track == 0)
         sync_track = 1;
      else
         sync_track = 0; 
      end
      
      if (track == 1) && (sync_track == 1)
          dual_rx_symbols(Nc+1:2*Nc) = rx_symbols(1:Nc).*conj(prev_rx_symbols(1:Nc)./abs(prev_rx_symbols(1:Nc)));
          dual_rx_symbols_float32 = []; k = 1;
          for i=1:2*Nc
              dual_rx_symbols_float32(k++) = real(dual_rx_symbols(i));
              dual_rx_symbols_float32(k++) = imag(dual_rx_symbols(i));
          end
          fwrite(fm, dual_rx_symbols_float32, "float32");
          dual_rx_bits(Nc*Nb+1:2*Nc*Nb) = rx_bits;
          %dump_bits(dual_rx_bits);
      else
          dual_rx_symbols(1:Nc) = rx_symbols(1:Nc).*conj(prev_rx_symbols(1:Nc)./abs(prev_rx_symbols(1:Nc)));
          dual_rx_bits(1:Nc*Nb) = rx_bits;
      end
    end

    % update some states

    prev_rx_symbols = rx_symbols;
    [sig_est noise_est] = snr_update(f, sig_est, noise_est, pd);
    snr_est = calc_snr(f, sig_est, noise_est);
    snr_est_log = [snr_est_log snr_est];
    foff -= 0.5*f_err;
    foff_log = [foff_log foff];

    % freq est state machine

    [sync reliable_sync_bit fest_state fest_timer sync_mem] = freq_state(f, sync_bit, fest_state, fest_timer, sync_mem);
    sync_log = [sync_log sync];

    % count bit errors if we find a test frame

    [test_frame_sync bit_errors error_pattern f] = put_test_bits(f, test_bits, rx_bits);
    if (test_frame_sync == 1)
      if (bit_errors)
        printf("fr: %d bit_errors: %d\n", fr, bit_errors);
      end
      total_bit_errors = total_bit_errors + bit_errors;
      total_bits = total_bits + f.Ntest_bits;
      bit_errors_log = [bit_errors_log bit_errors/f.Ntest_bits];
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
        else
          error_pattern_log = [error_pattern_log error_pattern];
        end
      end
    end

    test_frame_sync_state = next_test_frame_sync_state;
    test_frame_sync_log = [test_frame_sync_log test_frame_sync_state];
  end
 
  if nargin == 5
    fclose(fm);
    etfilename = strcat(strtok(symbolfilename,"."),"_et.bin");
    fet = fopen(etfilename, "wb");
    fwrite(fet, entered_track_log, "short");
    fclose(fet);
  end

  % ---------------------------------------------------------------------
  % Print Stats
  % ---------------------------------------------------------------------

  % Peak to Average Power Ratio calcs from http://www.dsplog.com

  papr = max(rx_fdm_log.*conj(rx_fdm_log)) / mean(rx_fdm_log.*conj(rx_fdm_log));
  papr_dB = 10*log10(papr);

  ber = total_bit_errors / total_bits;
  printf("%d bits  %d errors  BER: %1.4f PAPR(rx): %1.2f dB\n",total_bits, total_bit_errors, ber, papr_dB);

  % ---------------------------------------------------------------------
  % Plots
  % ---------------------------------------------------------------------

  xt = (1:frames)/Rs;
  secs = frames/Rs;

  figure(1); clf;
  [n m] = size(rx_symbols_log);
  plot(real(rx_symbols_log(1:Nc+1,15:m)),imag(rx_symbols_log(1:Nc+1,15:m)),'+')
  axis([-2 2 -2 2]);
  title('Scatter Diagram');

  figure(2); clf;
  plot(xt, rx_timing_log)
  title('timing offset (samples)');
  
  figure(3);
  plot(xt, foff_log, '-;freq offset;')
  %hold on;
  %plot(xt, sync_log*75, 'r;course-fine;');
  %hold off;
  title('Freq offset (Hz)');
  grid;

  figure(4); clf;
  plot_specgram(rx_fdm_log, Fs);

  figure(5); clf;
  subplot(311)
  stem(xt, sync_log)
  axis([0 secs 0 1.5]);
  title('BPSK Sync')
  subplot(312)
  stem(xt, bit_errors_log);
  title('Bit Errors for test frames')
  subplot(313)
  plot(xt, test_frame_sync_log);
  axis([0 secs 0 1.5]);
  title('Test Frame Sync')

  figure(6); clf;
  subplot(211);
  plot(xt, snr_est_log);
  title('SNR Estimates')
  subplot(212)
  snrdB_pc = 20*log10(sig_est(1:Nc+1)) - 20*log10(noise_est(1:Nc+1));
  bar(snrdB_pc(1:Nc) - mean(snrdB_pc(1:Nc)))
  axis([0 Nc+1 -3 3]);

  figure(7); clf;
  hold on;
  lep = length(error_pattern_log);
  if lep != 0 
    for p=1:Nc
      plot(p + 0.25*error_pattern_log((p-1)*2+1:Nc*Nb:lep));
      plot(0.30 + p + 0.25*error_pattern_log(p*2:Nc*Nb:lep),'r')
    end
    hold off;
    axis([1 lep/(Nc*Nb) 0 Nc])
  end

  figure(8); clf;
  subplot(211)
  [a b] = size(rx_fdm_log);
  xt1 = (1:b)/Fs;
  plot(xt1, rx_fdm_log);
  title('Rx FDM Signal');
  subplot(212)
  plot((0:Nspec/2-1)*Fs/Nspec, SdB(1:Nspec/2) - 20*log10(Nspec/2))
  axis([0 Fs/2 -40 0])
  grid
  title('FDM Rx Spectrum');

if 0
  % interleaving tests

  load ../unittest/inter560.txt
  lep = length(error_pattern_log);
  lep = floor(lep/560)*560;
  error_pattern_log_inter = zeros(1,lep);
  for i=1:560:lep
    for j=1:560
      %printf("i: %4d j: %4d inter560(j): %4d\n", i,j,inter560(j));
      index = inter560(j);
      error_pattern_log_inter(i-1+index+1) = error_pattern_log(i-1+j);
    end
  end

  figure(8)
  clf;
  hold on;
  for p=1:Nc
    plot(p + 0.25*error_pattern_log_inter((p-1)*2+1:Nc*Nb:lep));
    plot(0.30 + p + 0.25*error_pattern_log_inter(p*2:Nc*Nb:lep),'r')
  end
  hold off;
  axis([1 lep/(Nc*Nb) 0 Nc])
end

  % optionally save error pattern file

  if nargin == 4
    fout = fopen(errorpatternfilename, "wb");
    fwrite(fout, error_pattern_log, "short");
    fclose(fout);
  end


endfunction
