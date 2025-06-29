% fsk_lib.m
% David Rowe Oct 2015 - present
%
% mFSK modem, started out life as RTTY demodulator for Project
% Horus High Altitude Ballon (HAB) telemetry, also used for:
%
% FreeDV 2400A: 4FSK UHF/UHF digital voice
% Wenet.......: 100 kbit/s HAB High Def image telemetry
%
% Handles frequency offsets, performance right on ideal, C implementation
% in codec2/src

1;

function states = fsk_init(Fs, Rs, M=2, P=8, nsym=50)
  states.M = M;                    
  states.bitspersymbol = log2(M);
  states.Fs = Fs;
  states.Rs = Rs;

  states.nsym = nsym;                             % Number of symbols processed by demodulator in each call, also
                                                  % the timing estimator window
  Ts = states.Ts = Fs/Rs;                         % number of samples per symbol
  assert(Ts == floor(Ts), "Fs/Rs must be an integer");

  N = states.N = Ts*states.nsym;                  % processing buffer size, nice big window for timing est
  bin_width_Hz = 0.1*Rs;                          % we want enough DFT bins to get within 10% of the tones centre
  Ndft = Fs/bin_width_Hz;
  states.Ndft = 2.^ceil(log2(Ndft));              % round to nearest power of 2 for efficent FFT
  states.Sf = zeros(states.Ndft,1);               % current memory of dft mag samples
  states.tc = 0.1;                                % average DFT over longtime window, accurate at low Eb/No, but slow
  
  states.nbit = states.nsym*states.bitspersymbol; % number of bits per processing frame
  Nmem = states.Nmem  = N+2*Ts;                   % two symbol memory in down converted signals to allow for timing adj

  states.f_dc = zeros(M,Nmem);
  states.P = P;                                   % oversample rate out of filter
  assert(Ts/states.P == floor(Ts/states.P), "Ts/P must be an integer");

  states.tx_tone_separation = 2*Rs;
  states.nin = N;                                 % can be N +/- Ts/P samples to adjust for sample clock offsets
  states.verbose = 0;
  states.phi = zeros(1, M);                       % keep down converter osc phase continuous

  % BER stats 

  states.ber_state = 0;
  states.ber_valid_thresh = 0.05;  states.ber_invalid_thresh = 0.1; 
  states.Tbits = 0;
  states.Terrs = 0;
  states.nerr_log = 0;

  % extra simulation parameters

  states.tx_real = 1;
  states.dA(1:M) = 1;
  states.df(1:M) = 0;
  states.f(1:M) = 0;
  states.norm_rx_timing = 0;
  states.ppm = 0;
  states.prev_pkt = [];
 
  % Freq. estimator limits
  states.fest_fmax = Fs;
  states.fest_fmin = 0;
  states.fest_min_spacing = 0.75*Rs;
  states.freq_est_type = 'peak';

  %printf("Octave: M: %d Fs: %d Rs: %d Ts: %d nsym: %d nbit: %d N: %d Ndft: %d fmin: %d fmax: %d\n",
  %       states.M, states.Fs, states.Rs, states.Ts, states.nsym, states.nbit, states.N, states.Ndft, states.fest_fmin, states.fest_fmax);

endfunction


% modulator function

function tx = fsk_mod(states, tx_bits)

    M  = states.M;
    Ts = states.Ts;
    Fs = states.Fs;
    ftx  = states.ftx;
    df = states.df; % tone freq change in Hz/s
    dA = states.dA; % amplitude of each tone

    num_bits = length(tx_bits);
    num_symbols = num_bits/states.bitspersymbol;
    tx = zeros(states.Ts*num_symbols,1);
    tx_phase = 0;
    s = 1;

    for i=1:states.bitspersymbol:num_bits

      % map bits to FSK symbol (tone number)

      K = states.bitspersymbol;
      tone = tx_bits(i:i+(K-1)) * (2.^(K-1:-1:0))' + 1;
      
      tx_phase_vec = tx_phase + (1:Ts)*2*pi*ftx(tone)/Fs;
      tx_phase = tx_phase_vec(Ts) - floor(tx_phase_vec(Ts)/(2*pi))*2*pi;
      if states.tx_real
        tx((s-1)*Ts+1:s*Ts) = dA(tone)*2.0*cos(tx_phase_vec);
      else
        tx((s-1)*Ts+1:s*Ts) = dA(tone)*exp(j*tx_phase_vec);
      end
      s++;

      % freq drift

      ftx += df*Ts/Fs;
    end
    states.ftx = ftx;
endfunction


% Estimate the frequency of the FSK tones.  In some applications (such
% as balloon telemetry) these may not be well controlled by the
% transmitter, so we have to try to estimate them.

function states = est_freq(states, sf, ntones)
  N = states.N;
  Ndft = states.Ndft;
  Fs = states.Fs;
  
  % This assumption is OK for balloon telemetry but may not be true in
  % general

  min_tone_spacing = states.fest_min_spacing;
  
  % set some limits to search range, which will mean some manual re-tuning

  fmin = states.fest_fmin;
  fmax = states.fest_fmax;
  % note 0 Hz is mapped to Ndft/2+1 via fftshift
  st = floor(fmin*Ndft/Fs) + Ndft/2;  st = max(1,st);
  en = floor(fmax*Ndft/Fs) + Ndft/2;  en = min(Ndft,en);
  
  #printf("Fs: %f Ndft: %d fmin: %f fmax: %f st: %d en: %d\n",Fs, Ndft,  fmin, fmax, st, en)
  
  % Update mag DFT  ---------------------------------------------

  % we break up input buffer to a series of overlapping Ndft sequences
  numffts = floor(length(sf)/(Ndft/2)) - 1;
  h = hanning(Ndft);
  for i=1:numffts
    a = (i-1)*Ndft/2+1; b = a + Ndft - 1;
    Sf = abs(fftshift(fft(sf(a:b) .* h, Ndft)));

    % Smooth DFT mag spectrum, slower to respond to changes but more
    % accurate.  Single order IIR filter is an exponentially weighted
    % moving average.  This means the freq est window is wider than
    % timing est window
    tc = states.tc; states.Sf = (1-tc)*states.Sf + tc*Sf;
  end

  % Search for each tone method 1 - peak pick each tone location ----------------------------------

  f = []; a = [];
  Sf = states.Sf;
  for m=1:ntones
    [tone_amp tone_index] = max(Sf(st:en));
    tone_index += st - 1;
    
    f = [f (tone_index-1-Ndft/2)*Fs/Ndft];
    a = [a tone_amp];

    % zero out region min_tone_spacing either side of max so we can find next highest peak
    % closest spacing for non-coh mFSK is Rs

    stz = tone_index - floor((min_tone_spacing)*Ndft/Fs);
    stz = max(1,stz);
    enz = tone_index + floor((min_tone_spacing)*Ndft/Fs);
    enz = min(Ndft,enz);
    Sf(stz:enz) = 0;
  end

  states.f = sort(f);
  
  % Search for each tone method 2 - correlate with mask with non-zero entries at tone spacings -----

  % Create a mask with non-zero entries at tone spacing.  Might be
  % smarter to use the DFT of a hanning window as mask
  
  mask = zeros(1,Ndft);
  mask(1:3) = 1;
  for m=1:ntones-1
    bin = round(m*states.tx_tone_separation*Ndft/Fs);
    mask(bin:bin+2) = 1;
  end
  mask = mask(1:bin+2);
  states.mask = mask;
  
  % drag mask over Sf, looking for peak in correlation
  b_max = st; corr_max = 0;
  Sf = states.Sf; corr_log = [];
  for b=st:en-length(mask)
    corr = mask * Sf(b:b+length(mask)-1);
    corr_log = [corr_log corr];
    if corr > corr_max
      corr_max = corr;
      b_max = b;
    end
  end
  foff = ((b_max-1)-Ndft/2)*Fs/Ndft;
  
  if bitand(states.verbose, 0x8)
    % enable this to single step through frames
    figure(1); clf; subplot(211); plot(Sf,'b;sf;'); 
    hold on; plot(max(Sf)*[zeros(1,b_max) mask],'g;mask;'); hold off;
    subplot(212); plot(corr_log); ylabel('corr against f');
    printf("foff: %4.0f\n", foff);
    kbhit;
  end
  states.f2 = foff + (0:ntones-1)*states.tx_tone_separation;
end


% ------------------------------------------------------------------------------------
% Given a buffer of nin input Rs baud FSK samples, returns nsym bits.
%
% nin is the number of input samples required by demodulator.  This is
% time varying.  It will nominally be N (8000), and occasionally N +/- 
% Ts/2 (e.g. 8080 or 7920).  This is how we compensate for differences between the
% remote tx sample clock and our sample clock.  This function always returns
% N/Ts (e.g. 50) demodulated bits.  Variable number of input samples, constant number
% of output bits.

function [rx_bits states] = fsk_demod(states, sf)
  M = states.M;
  N = states.N;
  Ndft = states.Ndft;
  Fs = states.Fs;
  Rs = states.Rs;
  Ts = states.Ts;
  nsym = states.nsym;
  P = states.P;
  nin = states.nin;
  verbose = states.verbose;
  Nmem = states.Nmem;
  f = states.f;

  assert(length(sf) == nin);

  % down convert and filter at rate P ------------------------------

  % update filter (integrator) memory by shifting in nin samples
  
  nold = Nmem-nin; % number of old samples we retain

  f_dc = states.f_dc; 
  f_dc(:,1:nold) = f_dc(:,Nmem-nold+1:Nmem);

  % freq shift down to around DC, ensuring continuous phase from last frame, as nin may vary
  for m=1:M
    phi_vec = states.phi(m) + (1:nin)*2*pi*f(m)/Fs;
    f_dc(m,nold+1:Nmem) = sf .* exp(j*phi_vec)';
    states.phi(m)  = phi_vec(nin);
    states.phi(m) -= 2*pi*floor(states.phi(m)/(2*pi));
  end
  % save filter (integrator) memory for next time
  states.f_dc = f_dc;
  
  % integrate over symbol period, which is effectively a LPF, removing
  % the -2Fc frequency image.  Can also be interpreted as an ideal
  % integrate and dump, non-coherent demod.  We run the integrator at
  % rate P*Rs (1/P symbol offsets) to get outputs at a range of
  % different fine timing offsets.  We calculate integrator output
  % over nsym+1 symbols so we have extra samples for the fine timing
  % re-sampler at either end of the array.

  f_int = zeros(M,(nsym+1)*P);
  for i=1:(nsym+1)*P
    st = 1 + (i-1)*Ts/P;
    en = st+Ts-1;
    for m=1:M
      f_int(m,i) = sum(f_dc(m,st:en));
    end
  end
  states.f_int = f_int;
  
  % fine timing estimation -----------------------------------------------

  % Non linearity has a spectral line at Rs, with a phase
  % related to the fine timing offset.  See:
  %   http://www.rowetel.com/blog/?p=3573 
  % We have sampled the integrator output at Fs=P samples/symbol, so
  % lets do a single point DFT at w = 2*pi*f/Fs = 2*pi*Rs/(P*Rs)
  %
  % Note timing non-linearity derived by experiment.  Not quite sure what I'm doing here.....
  % but it gives 0dB impl loss for 2FSK Eb/No=9dB, testmode 1:
  %   Fs: 8000 Rs: 50 Ts: 160 nsym: 50
  %   frames: 200 Tbits: 9700 Terrs: 93 BER 0.010

  Np = length(f_int(1,:));
  w = 2*pi*(Rs)/(P*Rs);
  timing_nl = sum(abs(f_int(:,:)).^2);
  x = timing_nl * exp(-j*w*(0:Np-1))';
  norm_rx_timing = angle(x)/(2*pi);
  rx_timing = norm_rx_timing*P;

  states.x = x;
  states.timing_nl = timing_nl;
  states.rx_timing = rx_timing;
  prev_norm_rx_timing = states.norm_rx_timing;
  states.norm_rx_timing = norm_rx_timing;

  % estimate sample clock offset in ppm
  % d_norm_timing is fraction of symbol period shift over nsym symbols

  d_norm_rx_timing = norm_rx_timing - prev_norm_rx_timing;

  % filter out big jumps due to nin changes

  if abs(d_norm_rx_timing) < 0.2
    appm = 1E6*d_norm_rx_timing/nsym;
    states.ppm = 0.9*states.ppm + 0.1*appm;
  end

  % work out how many input samples we need on the next call. The aim
  % is to keep angle(x) away from the -pi/pi (+/- 0.5 fine timing
  % offset) discontinuity.  The side effect is to track sample clock
  % offsets

  next_nin = N;
  if norm_rx_timing > 0.25
     next_nin += Ts/4;
  end
  if norm_rx_timing < -0.25;
     next_nin -= Ts/4;
  end
  states.nin = next_nin;

  % Now we know the correct fine timing offset, Re-sample integrator
  % outputs using fine timing estimate and linear interpolation, then
  % extract the demodulated bits

  low_sample = floor(rx_timing);
  fract = rx_timing - low_sample;
  high_sample = ceil(rx_timing);

  if bitand(verbose,0x2)
    printf("rx_timing: %3.2f low_sample: %d high_sample: %d fract: %3.3f nin_next: %d\n", rx_timing, low_sample, high_sample, fract, next_nin);
  end

  f_int_resample = zeros(M,nsym);
  rx_bits = zeros(1,nsym*states.bitspersymbol);
  tone_max = zeros(1,nsym);
  rx_nse_pow = 1E-12; rx_sig_pow = 0.0;
  
  for i=1:nsym
    st = i*P+1;
    f_int_resample(:,i) = f_int(:,st+low_sample)*(1-fract) + f_int(:,st+high_sample)*fract;

    % Hard decision decoding, Largest amplitude tone is the winner.
    % Map this FSK "symbol" back to bits, depending on M

    [tone_max(i) tone_index] = max(f_int_resample(:,i));
    st = (i-1)*states.bitspersymbol + 1;
    en = st + states.bitspersymbol-1;
    arx_bits = dec2bin(tone_index - 1, states.bitspersymbol) - '0';
    rx_bits(st:en) = arx_bits;

    % each filter is the DFT of a chunk of spectrum.  If there is no tone in the
    % filter it can be considered an estimate of noise in that bandwidth
    rx_pows = f_int_resample(:,i) .* conj(f_int_resample(:,i));
    rx_sig_pow += rx_pows(tone_index);
    rx_nse_pow += (sum(rx_pows) - rx_pows(tone_index))/(M-1);
  end

  states.f_int_resample = f_int_resample;

  % Eb/No estimation (todo: this needs some work, like calibration, low Eb/No perf, work for all M)
  tone_max = abs(tone_max);
  states.EbNodB = -6 + 20*log10(1E-6+mean(tone_max)/(1E-6+std(tone_max)));

  % Estimators for LDPC decoder, might be a bit rough if nsym is small
  rx_sig_pow = rx_sig_pow/nsym;
  rx_nse_pow = rx_nse_pow/nsym;
  states.v_est = sqrt(rx_sig_pow-rx_nse_pow); 
  states.SNRest = rx_sig_pow/rx_nse_pow;
endfunction


% BER counter and test frame sync logic -------------------------------------------
% We look for test_frame in rx_bits_buf,  rx_bits_buf must be twice as long as test_frame

function states = ber_counter(states, test_frame, rx_bits_buf)
  nbit = length(test_frame);
  assert (length(rx_bits_buf) == 2*nbit);
  state = states.ber_state;
  next_state = state;

  if state == 0

    % try to sync up with test frame

    nerrs_min = nbit;
    for i=1:nbit
      error_positions = xor(rx_bits_buf(i:nbit+i-1), test_frame);
      nerrs = sum(error_positions);
      if nerrs < nerrs_min
        nerrs_min = nerrs;
        states.coarse_offset = i;
      end
    end
    if nerrs_min/nbit < states.ber_valid_thresh
      next_state = 1;
    end
    if bitand(states.verbose,0x4)
      printf("coarse offset: %d nerrs_min: %d next_state: %d\n", states.coarse_offset, nerrs_min, next_state);
    end
    states.nerr = nerrs_min;
  end

  if state == 1  

    % we're synced up, lets measure bit errors

    error_positions = xor(rx_bits_buf(states.coarse_offset:states.coarse_offset+nbit-1), test_frame);
    nerrs = sum(error_positions);
    if nerrs/nbit > states.ber_invalid_thresh
      next_state = 0;
      if bitand(states.verbose,0x4)
        printf("coarse offset: %d nerrs: %d next_state: %d\n", states.coarse_offset, nerrs, next_state);
      end
    else
      states.Terrs += nerrs;
      states.Tbits += nbit;
      states.nerr_log = [states.nerr_log nerrs];
    end
    states.nerr = nerrs;
  end

  states.ber_state = next_state;
endfunction


% Alternative stateless BER counter that works on packets that may have gaps between them

function states = ber_counter_packet(states, test_frame, rx_bits_buf)
  ntestframebits = states.ntestframebits;
  nbit = states.nbit;

  % look for offset with min errors

  nerrs_min = ntestframebits; coarse_offset = 1;
  for i=1:nbit
    error_positions = xor(rx_bits_buf(i:ntestframebits+i-1), test_frame);
    nerrs = sum(error_positions);
    %printf("i: %d nerrs: %d\n", i, nerrs);
    if nerrs < nerrs_min
      nerrs_min = nerrs;
      coarse_offset = i;
    end
  end

  % if less than threshold count errors

  if nerrs_min/ntestframebits < 0.05 
    states.Terrs += nerrs_min;
    states.Tbits += ntestframebits;
    states.nerr_log = [states.nerr_log nerrs_min];
    if bitand(states.verbose, 0x4)
      printf("coarse_offset: %d nerrs_min: %d\n", coarse_offset, nerrs_min);
    end
  end
endfunction


