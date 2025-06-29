% fdmdv.m
%
% Functions that implement a Frequency Divison Multiplexed Modem for
% Digital Voice (FDMDV) over HF channels.
%
% Copyright David Rowe 2012
% This program is distributed under the terms of the GNU General Public License 
% Version 2
%
% TODO:
%   [X] refactor with states
%   [X] remove commented out globals
%   [X] tfdmdv works
%   [X] fdmdv_demod works
%   [ ] fdmdv_ut works
 
% reqd to make sure we get same random bits at mod and demod

fdmdv_common;

rand('state',1); 
randn('state',1);

% Functions ----------------------------------------------------


function f = fdmdv_init(Nc=14)
    Fs   = f.Fs = 8000;      % sample rate in Hz
    T    = f.T  = 1/Fs;      % sample period in seconds
    Rs   = f.Rs = 50;        % symbol rate in Hz
           f.Nc = Nc;
    Nb   = f.Nb = 2;         % Bits/symbol for PSK modulation
    Rb   = f.Rb = Nc*Rs*Nb;  % bit rate
    M    = f.M  = Fs/Rs;     % oversampling factor
    Nsym = f.Nsym  = 6;      % number of symbols to filter over

    Fsep    = f.Fsep = 75;             % Separation between carriers (Hz)
    Fcenter = f.Fcentre = 1500;        % Centre frequency, Nc/2 carriers below this, 
                                       % Nc/2 carriers above (Hz)
    Nt      = f.Nt = 5;                % number of symbols we estimate timing over
    P       = f.P = 4;                 % oversample factor used for rx symbol filtering
    Nfilter = f.Nfilter = Nsym*M;

    Nfiltertiming = f.Nfiltertiming = M+Nfilter+M;

    Nsync_mem = f.Nsync_mem = 6;
    f.sync_uw = [1 -1 1 -1 1 -1];

    alpha = 0.5;
    f.gt_alpha5_root = gen_rn_coeffs(alpha, T, Rs, Nsym, M);

    f.pilot_bit = 0;                   % current value of pilot bit

    f.tx_filter_memory = zeros(Nc+1, Nfilter);
    f.rx_filter_memory = zeros(Nc+1, Nfilter);
    f.Nrx_fdm_mem = Nfilter+M+M/P;
    f.rx_fdm_mem = zeros(1,f.Nrx_fdm_mem);

    f.snr_coeff = 0.9;        % SNR est averaging filter coeff

    % phasors used for up and down converters

    f.freq = zeros(Nc+1,1);
    f.freq_pol = zeros(Nc+1,1);

    for c=1:Nc/2
      carrier_freq = (-Nc/2 - 1 + c)*Fsep;
      f.freq_pol(c)   = 2*pi*carrier_freq/Fs;
      f.freq(c)       = exp(j*f.freq_pol(c));
    end

    for c=floor(Nc/2)+1:Nc
      carrier_freq = (-Nc/2 + c)*Fsep;
      f.freq_pol(c)  = 2*pi*carrier_freq/Fs;
      f.freq(c)      = exp(j*f.freq_pol(c));
    end

    f.freq_pol(Nc+1)  = 2*pi*0/Fs;
    f.freq(Nc+1) = exp(j*f.freq_pol(Nc+1));

    f.fbb_rect = exp(j*2*pi*f.Fcentre/Fs);
    f.fbb_phase_tx = 1;
    f.fbb_phase_rx = 1;

    % Spread initial FDM carrier phase out as far as possible.  This
    % helped PAPR for a few dB.  We don't need to adjust rx phase as DQPSK
    % takes care of that.

    f.phase_tx = ones(Nc+1,1);
    f.phase_tx = exp(j*2*pi*(0:Nc)/(Nc+1));
    f.phase_rx = ones(Nc+1,1);

    % decimation filter

    f.Nrxdec = 31;
    % fir1() output appears to have changed from when coeffs used in C port were used
    %f.rxdec_coeff = fir1(f.Nrxdec-1, 0.25);
    f.rxdec_coeff = [-0.00125472 -0.00204605   -0.0019897  0.000163906  0.00490937 0.00986375  ...
                      0.0096718  -0.000480351  -0.019311  -0.0361822   -0.0341251  0.000827866 ...
                      0.0690577   0.152812      0.222115   0.249004     0.222115   0.152812    ...
                      0.0690577   0.000827866  -0.0341251 -0.0361822   -0.019311  -0.000480351 ...
                      0.0096718   0.00986375    0.00490937 0.000163906 -0.0019897 -0.00204605  ...
                      -0.00125472];

    % we need room for Nrdec + the max nin, as we may need to filter max_min samples
    
    f.Nrxdecmem = f.Nrxdec+M+M/P;
    f.rxdec_lpf_mem = zeros(1,f.Nrxdecmem);
    f.Q=M/4;

    % freq offset estimation

    f.Mpilotfft      = 256;
    f.Npilotcoeff    = 30;                              

    % here's how to make this filter from scratch, however it appeared to change over different
    % octave versions so have hard coded to version used for C port
    %f.pilot_coeff    = fir1(f.Npilotcoeff-1, 200/(Fs/2))'; % 200Hz LPF
    f.pilot_coeff    = [0.00223001 0.00301037 0.00471258 0.0075934 0.0118145 0.0174153 ...
                       0.0242969  0.0322204  0.0408199 0.0496286  0.0581172 0.0657392 ...
                       0.0719806  0.0764066  0.0787022 0.0787022  0.0764066 0.0719806 ...
                       0.0657392  0.0581172  0.0496286 0.0408199  0.0322204 0.0242969 ...
                       0.0174153  0.0118145  0.0075934 0.00471258 0.00301037 0.00223001];

    f.Npilotbaseband = f.Npilotcoeff + M + M/P;            % number of pilot baseband samples 
    f.Npilotlpf      = 4*M;                                % reqd for pilot LPF
                                                           % number of symbols we DFT pilot over
                                                           % pilot est window

    % pilot LUT, used for copy of pilot at rx
  
    f.pilot_lut = generate_pilot_lut(f);
    f.pilot_lut_index = 1;
    f.prev_pilot_lut_index = 3*M+1;

    % Freq offset estimator states 

    f.pilot_baseband1 = zeros(1, f.Npilotbaseband);        % pilot baseband samples
    f.pilot_baseband2 = zeros(1, f.Npilotbaseband);        % pilot baseband samples
    f.pilot_lpf1 = zeros(1, f.Npilotlpf);                  % LPF pilot samples
    f.pilot_lpf2 = zeros(1, f.Npilotlpf);                  % LPF pilot samples

    % Timing estimator states

    f.rx_filter_mem_timing = zeros(Nc+1, Nt*P);
    f.rx_baseband_mem_timing = zeros(Nc+1, f.Nfiltertiming);

    % Test bit stream state variables

    f = init_test_bits(f);
endfunction


% generate Nc+1 PSK symbols from vector of (1,Nc*Nb) input bits.  The
% Nc+1 symbol is the +1 -1 +1 .... BPSK sync carrier

function [tx_symbols f] = bits_to_psk(f, prev_tx_symbols, tx_bits)
  Nc = f.Nc; Nb = f.Nb;
  m4_gray_to_binary = [
    bin2dec("00") 
    bin2dec("01")
    bin2dec("11")
    bin2dec("10")
  ];
  m8_gray_to_binary = [
    bin2dec("000")
    bin2dec("001")
    bin2dec("011")
    bin2dec("010")
    bin2dec("111")
    bin2dec("110")
    bin2dec("100")
    bin2dec("101")
  ];

  assert(length(tx_bits) == Nc*Nb, "Incorrect number of bits");

  m = 2 .^ Nb;
  assert((m == 4) || (m == 8));

  for c=1:Nc

    % extract bits for this symbol

    bits_binary = tx_bits((c-1)*Nb+1:c*Nb); 
    bits_decimal = sum(bits_binary .* 2.^(Nb-1:-1:0)); 

    % determine phase shift using gray code mapping    

    if m == 4
       phase_shift = (2*pi/m)*m4_gray_to_binary(bits_decimal+1);
    else
       phase_shift = (2*pi/m)*m8_gray_to_binary(bits_decimal+1);
    end

    % apply phase shift from previous symbol

    tx_symbols(c) = exp(j*phase_shift) * prev_tx_symbols(c);
  end

  % +1 -1 +1 -1 BPSK sync carrier, once filtered becomes two spectral
  % lines at +/- Rs/2
 
  if f.pilot_bit
     tx_symbols(Nc+1) = -prev_tx_symbols(Nc+1);
  else
     tx_symbols(Nc+1) = prev_tx_symbols(Nc+1);
  end
  if f.pilot_bit 
    f.pilot_bit = 0;
  else
    f.pilot_bit = 1;
  end

endfunction

% LP filter +/- 1000 Hz, allows us to perform rx filtering at a lower rate saving CPU

function [rx_fdm_filter fdmdv] = rxdec_filter(fdmdv, rx_fdm, nin)
  M = fdmdv.M;
  Nrxdecmem = fdmdv.Nrxdecmem;
  Nrxdec = fdmdv.Nrxdec;
  rxdec_coeff = fdmdv.rxdec_coeff;
  rxdec_lpf_mem = fdmdv.rxdec_lpf_mem;

  % place latest nin samples at end of buffer
  
  rxdec_lpf_mem(1:Nrxdecmem-nin) = rxdec_lpf_mem(nin+1:Nrxdecmem);
  rxdec_lpf_mem(Nrxdecmem-nin+1:Nrxdecmem) = rx_fdm(1:nin);
  
  % init room for nin output samples
  
  rx_fdm_filter = zeros(1,nin);

  % Move starting point for filter dot product to filter newest samples
  % in buffer.  This stuff makes my head hurt.
  
  st = Nrxdecmem - nin - Nrxdec + 1;
  for i=1:nin
    a = st+1; b = st+i+Nrxdec-1;
    %printf("nin: %d i: %d a: %d b: %d\n", nin, i, a, b);
    rx_fdm_filter(i) = rxdec_lpf_mem(st+i:st+i+Nrxdec-1) * rxdec_coeff';
  end

  fdmdv.rxdec_lpf_mem = rxdec_lpf_mem;
end


% Combined down convert and rx filter, more memory efficient but less intuitive design
% TODO: Decimate mem update and downconversion, this will save some more CPU and memory
%       note phase would have to advance 4 times as fast

function [rx_filt fdmdv] = down_convert_and_rx_filter(fdmdv, rx_fdm, nin, dec_rate)
  Nc = fdmdv.Nc;
  M = fdmdv.M;
  P = fdmdv.P;
  rx_fdm_mem = fdmdv.rx_fdm_mem;
  Nrx_fdm_mem = fdmdv.Nrx_fdm_mem;
  phase_rx = fdmdv.phase_rx;
  freq = fdmdv.freq;
  freq_pol = fdmdv.freq_pol;
  Nfilter = fdmdv.Nfilter;
  gt_alpha5_root = fdmdv.gt_alpha5_root;
  Q = fdmdv.Q;

  % update memory of rx_fdm_mem, newest nin sample ast end of buffer

  rx_fdm_mem(1:Nrx_fdm_mem-nin) = rx_fdm_mem(nin+1:Nrx_fdm_mem);
  rx_fdm_mem(Nrx_fdm_mem-nin+1:Nrx_fdm_mem) = rx_fdm(1:nin);

  for c=1:Nc+1

     #{
     So we have rx_fdm_mem, a baseband array of samples at
     rate Fs Hz, including the last nin samples at the end.  To
     filter each symbol we require the baseband samples for all Nsym
     symbols that we filter over.  So we need to downconvert the
     entire rx_fdm_mem array.  To downconvert these we need the LO
     phase referenced to the start of the rx_fdm_mem array.

      
      <--------------- Nrx_filt_mem ------->
                                     nin
      |--------------------------|---------|
       1                          |
                              phase_rx(c)
     
      This means winding phase(c) back from this point
      to ensure phase continuity.
     #}

     wind_back_phase = -freq_pol(c)*(Nfilter);
     phase_rx(c)     =  phase_rx(c)*exp(j*wind_back_phase);
    
     % down convert all samples in buffer

     rx_baseband = zeros(1,Nrx_fdm_mem);
     
     st  = Nrx_fdm_mem;    % end of buffer
     st -= nin-1;          % first new sample
     st -= Nfilter;        % first sample used in filtering

     %printf("Nfilter: %d Nrx_fdm_mem: %d dec_rate: %d nin: %d st: %d\n",
     %        Nfilter, Nrx_fdm_mem, dec_rate, nin,  st);
             
     f_rect = freq(c) .^ dec_rate;

     for i=st:dec_rate:Nrx_fdm_mem
        phase_rx(c) = phase_rx(c) * f_rect;
	rx_baseband(i) = rx_fdm_mem(i)*phase_rx(c)';
     end
 
     % now we can filter this carrier's P (+/-1) symbols.  Due to
     % filtering of rx_fdm we can filter at rate at rate M/Q

     N=M/P; k = 1;
     for i=1:N:nin
       rx_filt(c,k) = (M/Q)*rx_baseband(st+i-1:dec_rate:st+i-1+Nfilter-1) * gt_alpha5_root(1:dec_rate:length(gt_alpha5_root))';
       k+=1;
     end
  end

  fdmdv.phase_rx   = phase_rx;
  fdmdv.rx_fdm_mem = rx_fdm_mem;
endfunction


% LPF and peak pick part of freq est, put in a function as we call it twice

function [foff imax pilot_lpf_out S] = lpf_peak_pick(f, pilot_baseband, pilot_lpf, nin, do_fft)
  M = f.M;
  Npilotlpf = f.Npilotlpf;
  Npilotbaseband = f.Npilotbaseband;
  Npilotcoeff = f.Npilotcoeff;
  Fs = f.Fs;
  Mpilotfft = f.Mpilotfft;
  pilot_coeff = f.pilot_coeff;

  % LPF cutoff 200Hz, so we can handle max +/- 200 Hz freq offset

  pilot_lpf(1:Npilotlpf-nin) = pilot_lpf(nin+1:Npilotlpf);
  k = Npilotbaseband-nin+1;;
  for i = Npilotlpf-nin+1:Npilotlpf
    pilot_lpf(i) = pilot_baseband(k-Npilotcoeff+1:k) * pilot_coeff';
    k++;
  end
  
  imax = 0;
  foff = 0;
  S = zeros(1, Mpilotfft);

  if do_fft
    % decimate to improve DFT resolution, window and DFT

    Mpilot = Fs/(2*200);  % calc decimation rate given new sample rate is twice LPF freq
    h = hanning(Npilotlpf);
    s = pilot_lpf(1:Mpilot:Npilotlpf) .* h(1:Mpilot:Npilotlpf)';
    s = [s zeros(1,Mpilotfft-Npilotlpf/Mpilot)];
    S = fft(s, Mpilotfft);

    % peak pick and convert to Hz

    [imax ix] = max(abs(S));
    r = 2*200/Mpilotfft;     % maps FFT bin to frequency in Hz
  
    if ix > Mpilotfft/2
      foff = (ix - Mpilotfft - 1)*r;
    else
      foff = (ix - 1)*r;
    endif
  end

  pilot_lpf_out = pilot_lpf;

endfunction


% Estimate frequency offset of FDM signal using BPSK pilot.  This is quite
% sensitive to pilot tone level wrt other carriers

function [foff S1 S2 f] = rx_est_freq_offset(f, rx_fdm, pilot, pilot_prev, nin, do_fft)
  M = f.M;
  Npilotbaseband = f.Npilotbaseband;
  pilot_baseband1 = f.pilot_baseband1;
  pilot_baseband2 = f.pilot_baseband2;
  pilot_lpf1 = f.pilot_lpf1;
  pilot_lpf2 = f.pilot_lpf2;

  % down convert latest nin samples of pilot by multiplying by ideal
  % BPSK pilot signal we have generated locally.  The peak of the DFT
  % of the resulting signal is sensitive to the time shift between the
  % received and local version of the pilot, so we do it twice at
  % different time shifts and choose the maximum.
 
  pilot_baseband1(1:Npilotbaseband-nin) = pilot_baseband1(nin+1:Npilotbaseband);
  pilot_baseband2(1:Npilotbaseband-nin) = pilot_baseband2(nin+1:Npilotbaseband);
  for i=1:nin
    pilot_baseband1(Npilotbaseband-nin+i) = rx_fdm(i) * conj(pilot(i)); 
    pilot_baseband2(Npilotbaseband-nin+i) = rx_fdm(i) * conj(pilot_prev(i)); 
  end

  [foff1 max1 pilot_lpf1 S1] = lpf_peak_pick(f, pilot_baseband1, pilot_lpf1, nin, do_fft);
  [foff2 max2 pilot_lpf2 S2] = lpf_peak_pick(f, pilot_baseband2, pilot_lpf2, nin, do_fft);

  if max1 > max2
    foff = foff1;
  else
    foff = foff2;
  end  

  f.pilot_baseband1 = pilot_baseband1;
  f.pilot_baseband2 = pilot_baseband2;
  f.pilot_lpf1 = pilot_lpf1;
  f.pilot_lpf2 = pilot_lpf2;
endfunction

% Experimental "feed forward" phase estimation function - estimates
% phase over a windows of Nph (e.g. Nph = 9) symbols.  May not work
% well on HF channels but lets see.  Has a phase ambiguity of m(pi/4)
% where m=0,1,2 which needs to be corrected outside of this function

function [phase_offsets ferr f] = rx_est_phase(f, rx_symbols)
  global rx_symbols_mem;
  global prev_phase_offsets;
  global phase_amb;
  Nph = f.Nph;
  Nc = f.Nc;

  % keep record of Nph symbols

  rx_symbols_mem(:,1:Nph-1) = rx_symbols_mem(:,2:Nph);
  rx_symbols_mem(:,Nph) = rx_symbols;
 
  % estimate and correct phase offset based of modulation stripped samples

  phase_offsets = zeros(Nc+1,1);
  for c=1:Nc+1

    % rotate QPSK constellation to a single point
    mod_stripped = abs(rx_symbols_mem(c,:)) .* exp(j*4*angle(rx_symbols_mem(c,:)));
    
    % find average phase offset, which will be on -pi/4 .. pi/4
    sum_real = sum(real(mod_stripped));
    sum_imag = sum(imag(mod_stripped));
    phase_offsets(c) = atan2(sum_imag, sum_real)/4;

    % determine if phase has jumped from - -> +    
    if (prev_phase_offsets(c) < -pi/8) && (phase_offsets(c) > pi/8)
      phase_amb(c) -= pi/2;
      if (phase_amb(c) < -pi)
        phase_amb(c) += 2*pi;
      end
    end
    
    % determine if phase has jumped from + -> -    
    if (prev_phase_offsets(c) > pi/8) && (phase_offsets(c) < -pi/8)
      phase_amb(c) += pi/2;
      if (phase_amb(c) > pi)
        phase_amb(c) -= 2*pi;
      end
    end
  end

  ferr = mean(phase_offsets - prev_phase_offsets);
  prev_phase_offsets = phase_offsets;

endfunction


% convert symbols back to an array of bits

function [rx_bits sync_bit f_err phase_difference] = psk_to_bits(f, prev_rx_symbols, rx_symbols, modulation)
  Nc = f.Nc;
  Nb = f.Nb;

  m4_binary_to_gray = [
    bin2dec("00") 
    bin2dec("01")
    bin2dec("11")
    bin2dec("10")
  ];

  m8_binary_to_gray = [
    bin2dec("000")
    bin2dec("001")
    bin2dec("011")
    bin2dec("010")
    bin2dec("110")
    bin2dec("111")
    bin2dec("101")
    bin2dec("100")
  ];

  m = 2 .^ Nb;
  assert((m == 4) || (m == 8));

  phase_difference = zeros(Nc+1,1);
  for c=1:Nc 
     norm = 1/(1E-6+abs(prev_rx_symbols(c)));  
     phase_difference(c) = rx_symbols(c) .* conj(prev_rx_symbols(c)) * norm;
  end

  for c=1:Nc
    phase_difference(c) *= exp(j*pi/4);

    if m == 4

        % to get a good match between C and Octave during start up use same as C code

        d = phase_difference(c);
        if (real(d) >= 0) && (imag(d) >= 0)
          msb = 0; lsb = 0;
        end
        if (real(d) < 0) && (imag(d) >= 0)
          msb = 0; lsb = 1;
        end
        if (real(d) < 0) && (imag(d) < 0)
          msb = 1; lsb = 1;
        end
        if (real(d) >= 0) && (imag(d) < 0)
          msb = 1; lsb = 0;
        end
          
        rx_bits(2*(c-1)+1) = msb;
        rx_bits(2*c) = lsb;
    else
      % determine index of constellation point received 0,1,...,m-1

      index = floor(angle(phase_difference(c))*m/(2*pi) + 0.5);

      if index < 0
        index += m;
      end

      % map to decimal version of bits encoded in symbol

      if m == 4
        bits_decimal = m4_binary_to_gray(index+1);
      else
        bits_decimal = m8_binary_to_gray(index+1);
      end
    
      % convert back to an array of received bits

      for i=1:Nb
        if bitand(bits_decimal, 2.^(Nb-i))
          rx_bits((c-1)*Nb+i) = 1;
        else
          rx_bits((c-1)*Nb+i) = 0;
        end
      end
    end
  end

  assert(length(rx_bits) == Nc*Nb);

  % Extract DBPSK encoded Sync bit

  norm = 1/(1E-6+abs(prev_rx_symbols(Nc+1)));
  phase_difference(Nc+1) = rx_symbols(Nc+1) * conj(prev_rx_symbols(Nc+1)) * norm;
  if (real(phase_difference(Nc+1)) < 0)
    sync_bit = 1;
    f_err = imag(phase_difference(Nc+1))*norm;  % make f_err magnitude insensitive
  else
    sync_bit = 0;
    f_err = -imag(phase_difference(Nc+1))*norm;
  end

  % extra pi/4 rotation as we need for snr_update and scatter diagram
  
  phase_difference(Nc+1) *= exp(j*pi/4);
  
endfunction


% given phase differences update estimates of signal and noise levels

function [sig_est noise_est] = snr_update(f, sig_est, noise_est, phase_difference)
    snr_coeff = f.snr_coeff;
    Nc = f.Nc;

    % mag of each symbol is distance from origin, this gives us a
    % vector of mags, one for each carrier.

    s = abs(phase_difference);
    
    % signal mag estimate for each carrier is a smoothed version
    % of instantaneous magntitude, this gives us a vector of smoothed
    % mag estimates, one for each carrier.
    
    sig_est = snr_coeff*sig_est + (1 - snr_coeff)*s;

    %printf("s: %f sig_est: %f snr_coeff: %f\n", s(1), sig_est(1), snr_coeff);

    % noise mag estimate is distance of current symbol from average
    % location of that symbol.  We reflect all symbols into the first
    % quadrant for convenience.
    
    refl_symbols = abs(real(phase_difference)) + j*abs(imag(phase_difference));    
    n = abs(exp(j*pi/4)*sig_est - refl_symbols);
     
    % noise mag estimate for each carrier is a smoothed version of
    % instantaneous noise mag, this gives us a vector of smoothed
    % noise power estimates, one for each carrier.

    noise_est = snr_coeff*noise_est + (1 - snr_coeff)*n;

endfunction


% calculate current sig estimate for eeach carrier

function snr_dB = calc_snr(f, sig_est, noise_est)
  Rs = f.Rs;

  % find total signal power by summing power in all carriers

  S = sum(sig_est .^2);
  SdB = 10*log10(S);

  % Average noise mag across all carriers and square to get an average
  % noise power.  This is an estimate of the noise power in Rs = 50Hz of
  % BW (note for raised root cosine filters Rs is the noise BW of the
  % filter)

  N50 = mean(noise_est).^2;
  N50dB = 10*log10(N50);

  % Now multiply by (3000 Hz)/(50 Hz) to find the total noise power in
  % 3000 Hz

  N3000dB = N50dB + 10*log10(3000/Rs);

  snr_dB = SdB - N3000dB;

endfunction


% sets up test bits system.  make sure rand('state', 1) has just beed called
% so we generate the right test_bits pattern!

function f = init_test_bits(f)
  f.Ntest_bits  = f.Nc*f.Nb*4;                % length of test sequence
  f.test_bits = rand(1,f.Ntest_bits) > 0.5;   % test pattern of bits
  f.current_test_bit = 1;
  f.rx_test_bits_mem = zeros(1,f.Ntest_bits);
endfunction


% returns nbits from a repeating sequence of random data

function [bits f] = get_test_bits(f, nbits)

  for i=1:nbits
    bits(i) = f.test_bits(f.current_test_bit++);
    
    if (f.current_test_bit > f.Ntest_bits)
      f.current_test_bit = 1;
    endif
  end
 
endfunction


% Accepts nbits from rx and attempts to sync with test_bits sequence.
% if sync OK measures bit errors

function [sync bit_errors error_pattern f] = put_test_bits(f, test_bits, rx_bits)
  Ntest_bits = f.Ntest_bits;      
  rx_test_bits_mem = f.rx_test_bits_mem;

  % Append to our memory

  [m n] = size(rx_bits);
  f.rx_test_bits_mem(1:f.Ntest_bits-n) = f.rx_test_bits_mem(n+1:f.Ntest_bits);
  f.rx_test_bits_mem(f.Ntest_bits-n+1:f.Ntest_bits) = rx_bits;

  % see how many bit errors we get when checked against test sequence

  error_pattern = xor(test_bits, f.rx_test_bits_mem);
  bit_errors = sum(error_pattern);

  % if less than a thresh we are aligned and in sync with test sequence

  ber = bit_errors/f.Ntest_bits;
  
  sync = 0;
  if (ber < 0.2)
    sync = 1;
  endif
endfunction

% Generate M samples of DBPSK pilot signal for Freq offset estimation

function [pilot_fdm bit symbol filter_mem phase] = generate_pilot_fdm(f, bit, symbol, filter_mem, phase, freq)
  M = f.M;
  Nfilter = f.Nfilter;
  gt_alpha5_root = f.gt_alpha5_root;

  % +1 -1 +1 -1 DBPSK sync carrier, once filtered becomes two spectral
  % lines at +/- Rs/2
 
  if bit
     symbol = -symbol;
  else
     symbol = symbol;
  end
  if bit 
    bit = 0;
  else
    bit = 1;
  end

  % filter DPSK symbol to create M baseband samples

  filter_mem(Nfilter) = (sqrt(2)/2)*symbol;
  for i=1:M
    tx_baseband(i) = M*filter_mem(M:M:Nfilter) * gt_alpha5_root(M-i+1:M:Nfilter)';
  end
  filter_mem(1:Nfilter-M) = filter_mem(M+1:Nfilter);
  filter_mem(Nfilter-M+1:Nfilter) = zeros(1,M);

  % upconvert

  for i=1:M
    phase = phase * freq;
    pilot_fdm(i) = sqrt(2)*2*tx_baseband(i)*phase;
  end

endfunction


% Generate a 4M sample vector of DBPSK pilot signal.  As the pilot signal
% is periodic in 4M samples we can then use this vector as a look up table
% for pilot signal generation in the demod.

function pilot_lut = generate_pilot_lut(f)
  Nc = f.Nc;
  Nfilter = f.Nfilter;
  M = f.M;
  freq = f.freq;

  % pilot states

  pilot_rx_bit = 0;
  pilot_symbol = sqrt(2);
  pilot_freq = freq(Nc+1);
  pilot_phase = 1;
  pilot_filter_mem = zeros(1, Nfilter);
  %prev_pilot = zeros(M,1);

  pilot_lut = [];

  F=8;

  for fr=1:F
    [pilot pilot_rx_bit pilot_symbol pilot_filter_mem pilot_phase] = generate_pilot_fdm(f, pilot_rx_bit, pilot_symbol, pilot_filter_mem, pilot_phase, pilot_freq);
    %prev_pilot = pilot;
    pilot_lut = [pilot_lut pilot];
  end

  % discard first 4 symbols as filter memory is filling, just keep last
  % four symbols

  pilot_lut = pilot_lut(4*M+1:M*F);

endfunction


% grab next pilot samples for freq offset estimation at demod

function [pilot prev_pilot pilot_lut_index prev_pilot_lut_index] = get_pilot(f, pilot_lut_index, prev_pilot_lut_index, nin)
  M = f.M;
  pilot_lut = f.pilot_lut;

  for i=1:nin
    pilot(i) = pilot_lut(pilot_lut_index);
    pilot_lut_index++;
    if pilot_lut_index > 4*M
      pilot_lut_index = 1;
    end
    prev_pilot(i) = pilot_lut(prev_pilot_lut_index);
    prev_pilot_lut_index++;
    if prev_pilot_lut_index > 4*M
      prev_pilot_lut_index = 1;
    end
  end
endfunction


% freq offset state machine.  Moves between acquire and track states based
% on BPSK pilot sequence.  Freq offset estimator occasionally makes mistakes
% when used continuously.  So we use it until we have acquired the BPSK pilot,
% then switch to a more robust tracking algorithm.  If we lose sync we switch
% back to acquire mode for fast-requisition.

function [sync reliable_sync_bit state timer sync_mem] = freq_state(f, sync_bit, state, timer, sync_mem)
  Nsync_mem = f.Nsync_mem;
  sync_uw = f.sync_uw;

  % look for 6 symbol (120ms) 010101 of sync sequence

  unique_word = 0;
  for i=1:Nsync_mem-1
    sync_mem(i) = sync_mem(i+1);
  end
  sync_mem(Nsync_mem) = 1 - 2*sync_bit;
  corr = 0;
  for i=1:Nsync_mem
    corr += sync_mem(i)*sync_uw(i);
  end
  if abs(corr) == Nsync_mem
    unique_word = 1;
  end
  reliable_sync_bit = (corr == Nsync_mem);
  
  % iterate state machine

  next_state = state;
  if state == 0
    if unique_word
      next_state = 1;
      timer = 0;
    end        
  end
  if state == 1
    if unique_word
      timer++;
      if timer == 25       % sync has been good for 500ms
        next_state = 2;
      end
    else 
      next_state = 0;
    end        
  end
  if state == 2            % good sync state
    if unique_word == 0
      timer = 0;
      next_state = 3;
    end
  end
  if state == 3            % tenative bad  state, but could be a fade
    if unique_word
      next_state = 2;
    else 
      timer++;
      if timer == 50       % wait for 1000ms in case sync comes back  
        next_state = 0;
      end
    end        
  end

  %printf("corr: % -d state: %d next_state: %d uw: %d timer: %d\n", corr, state, next_state, unique_word, timer);
  state = next_state;

  if state
    sync = 1;
  else
    sync = 0;
  end
endfunction

% Save test bits to a text file in the form of a C array

function test_bits_file(filename)
  global test_bits;
  global Ntest_bits;

  f=fopen(filename,"wt");
  fprintf(f,"/* Generated by test_bits_file() Octave function */\n\n");
  fprintf(f,"const int test_bits[]={\n");
  for m=1:Ntest_bits-1
    fprintf(f,"  %d,\n",test_bits(m));
  endfor
  fprintf(f,"  %d\n};\n",test_bits(Ntest_bits));
  fclose(f);
endfunction


% Saves RN filter coeffs to a text file in the form of a C array

function rn_file(gt_alpha5_root, filename)
  Nfilter = length(gt_alpha5_root);

  f=fopen(filename,"wt");
  fprintf(f,"/* Generated by rn_file() Octave function */\n\n");
  fprintf(f,"const float gt_alpha5_root[]={\n");
  for m=1:Nfilter-1
    fprintf(f,"  %g,\n",gt_alpha5_root(m));
  endfor
  fprintf(f,"  %g\n};\n",gt_alpha5_root(Nfilter));
  fclose(f);
endfunction


% Saves rx decimation filter coeffs to a text file in the form of a C array

function rxdec_file(fdmdv, filename)
  rxdec_coeff = fdmdv.rxdec_coeff;
  Nrxdec = fdmdv.Nrxdec;

  f=fopen(filename,"wt");
  fprintf(f,"/* Generated by rxdec_file() Octave function */\n\n");
  fprintf(f,"const float rxdec_coeff[]={\n");
  for m=1:Nrxdec-1
    fprintf(f,"  %g,\n",rxdec_coeff(m));
  endfor
  fprintf(f,"  %g\n};\n",rxdec_coeff(Nrxdec));
  fclose(f);
endfunction


function pilot_coeff_file(fdmdv, filename)
  pilot_coeff = fdmdv.pilot_coeff;
  Npilotcoeff = fdmdv.Npilotcoeff;

  f=fopen(filename,"wt");
  fprintf(f,"/* Generated by pilot_coeff_file() Octave function */\n\n");
  fprintf(f,"const float pilot_coeff[]={\n");
  for m=1:Npilotcoeff-1
    fprintf(f,"  %g,\n",pilot_coeff(m));
  endfor
  fprintf(f,"  %g\n};\n",pilot_coeff(Npilotcoeff));
  fclose(f);
endfunction


% Saves hanning window coeffs to a text file in the form of a C array

function hanning_file(fdmdv, filename)
  Npilotlpf = fdmdv.Npilotlpf;

  h = hanning(Npilotlpf);

  f=fopen(filename,"wt");
  fprintf(f,"/* Generated by hanning_file() Octave function */\n\n");
  fprintf(f,"const float hanning[]={\n");
  for m=1:Npilotlpf-1
    fprintf(f,"  %g,\n", h(m));
  endfor
  fprintf(f,"  %g\n};\n", h(Npilotlpf));
  fclose(f);
endfunction


function png_file(fig, pngfilename)
  figure(fig);

  pngname = sprintf("%s.png",pngfilename);
  print(pngname, '-dpng', "-S500,500")
  pngname = sprintf("%s_large.png",pngfilename);
  print(pngname, '-dpng', "-S800,600")
endfunction


% dump rx_bits in hex

function dump_bits(rx_bits)

    % pack into bytes, MSB first

    packed = zeros(1,floor(length(rx_bits)+7)/8);
    bit = 7; byte = 1;
    for i=1:length(rx_bits)
        packed(byte) = bitor(packed(byte), bitshift(rx_bits(i),bit));
        bit--;
        if (bit < 0)
            bit = 7;
            byte++;
        end 
    end

    for i=1:length(packed)
        printf("0x%02x ", packed(i)); 
    end
    printf("\n");

endfunction

