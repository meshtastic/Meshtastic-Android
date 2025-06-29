% fdmdv_common.m
%
% Functions that implement a Frequency Divison Multiplexed Modem functions
%

1;

% Given Nc symbols construct M samples (1 symbol) of Nc filtered
% symbols streams

function [tx_baseband fdmdv] = tx_filter(fdmdv, tx_symbols)
  Nc = fdmdv.Nc;
  M = fdmdv.M;
  tx_filter_memory = fdmdv.tx_filter_memory;
  Nfilter = fdmdv.Nfilter;
  gt_alpha5_root = fdmdv.gt_alpha5_root;

  tx_baseband = zeros(Nc+1,M);

  % tx filter each symbol, generate M filtered output samples for each symbol.
  % Efficient polyphase filter techniques used as tx_filter_memory is sparse
  
  tx_filter_memory(:,Nfilter) = sqrt(2)/2*tx_symbols;

  for i=1:M
    tx_baseband(:,i) = M*tx_filter_memory(:,M:M:Nfilter) * gt_alpha5_root(M-i+1:M:Nfilter)';
  end
  tx_filter_memory(:,1:Nfilter-M) = tx_filter_memory(:,M+1:Nfilter);
  tx_filter_memory(:,Nfilter-M+1:Nfilter) = zeros(Nc+1,M);

  fdmdv.tx_filter_memory = tx_filter_memory;
endfunction

% Construct FDM signal by frequency shifting each filtered symbol
% stream.  Returns complex signal so we can apply frequency offsets
% easily.

function [tx_fdm fdmdv] = fdm_upconvert(fdmdv, tx_filt)
  Fs = fdmdv.Fs;
  M = fdmdv.M;
  Nc = fdmdv.Nc;
  Fsep = fdmdv.Fsep;
  phase_tx = fdmdv.phase_tx;
  freq = fdmdv.freq;
  fbb_rect = fdmdv.fbb_rect;
  fbb_phase_tx = fdmdv.fbb_phase_tx;

  tx_fdm = zeros(1,M);

  % Nc+1 tones
  
  for c=1:Nc+1
      for i=1:M
        phase_tx(c) = phase_tx(c) * freq(c);
	tx_fdm(i) = tx_fdm(i) + tx_filt(c,i)*phase_tx(c);
      end
  end
 
  % shift up to carrier freq

  for i=1:M
    fbb_phase_tx *= fbb_rect;
    tx_fdm(i)     = tx_fdm(i) * fbb_phase_tx;  
  end

  % Scale such that total Carrier power C of real(tx_fdm) = Nc.  This
  % excludes the power of the pilot tone.
  % We return the complex (single sided) signal to make frequency
  % shifting for the purpose of testing easier

  tx_fdm = 2*tx_fdm;

  % normalise digital oscillators as the magnitude can drift over time

  for c=1:Nc+1
    mag = abs(phase_tx(c));
    phase_tx(c) /= mag;
  end
  mag = abs(fbb_phase_tx);
  fbb_phase_tx /= mag;

  fdmdv.fbb_phase_tx = fbb_phase_tx;
  fdmdv.phase_tx = phase_tx;
endfunction

% complex freq shifting helper function

function [out phase] = freq_shift(in, freqHz, Fs, phase)
  freq_rect = exp(j*2*pi*freqHz/Fs);

  out = zeros(1, length(in));
  for r=1:length(in)
    phase *= freq_rect;
    out(r) = in(r)*phase;
  end

  mag = abs(phase);
  phase /= mag;
endfunction

% Frequency shift each modem carrier down to Nc+1 baseband signals

function [rx_baseband fdmdv] = fdm_downconvert(fdmdv, rx_fdm, nin)
  Fs = fdmdv.Fs;
  M = fdmdv.M;
  Nc = fdmdv.Nc;
  phase_rx = fdmdv.phase_rx;
  freq = fdmdv.freq;

  rx_baseband = zeros(Nc+1,nin);
  
  for c=1:Nc+1
      for i=1:nin
        phase_rx(c) = phase_rx(c) * freq(c);
	rx_baseband(c,i) = rx_fdm(i)*phase_rx(c)';
      end
  end

  for c=1:Nc+1
    mag = abs(phase_rx(c));
    phase_rx(c) /= mag;
  end

  fdmdv.phase_rx = phase_rx;
endfunction

% Receive filter each baseband signal at oversample rate P

function [rx_filt fdmdv] = rx_filter(fdmdv, rx_baseband, nin)
  Nc = fdmdv.Nc;
  M = fdmdv.M;
  P = fdmdv.P;
  rx_filter_memory = fdmdv.rx_filter_memory;
  Nfilter = fdmdv.Nfilter;
  gt_alpha5_root = fdmdv.gt_alpha5_root;

  rx_filt = zeros(Nc+1,nin*P/M);

  % rx filter each symbol, generate P filtered output samples for each symbol.
  % Note we keep memory at rate M, it's just the filter output at rate P

  assert(mod(M,P)==0);
  N=M/P;
  j=1;
  for i=1:N:nin
    rx_filter_memory(:,Nfilter-N+1:Nfilter) = rx_baseband(:,i:i-1+N);
    rx_filt(:,j) = rx_filter_memory * gt_alpha5_root';
    rx_filter_memory(:,1:Nfilter-N) = rx_filter_memory(:,1+N:Nfilter);
    j+=1;
  end

  fdmdv.rx_filter_memory = rx_filter_memory;
endfunction

% Estimate optimum timing offset, re-filter receive symbols

function [rx_symbols rx_timing_M env fdmdv] = rx_est_timing(fdmdv, rx_filt, nin)
  M = fdmdv.M;
  Nt = fdmdv.Nt;
  Nc = fdmdv.Nc;
  rx_filter_mem_timing = fdmdv.rx_filter_mem_timing;
  P = fdmdv.P;
  Nfilter = fdmdv.Nfilter;
  Nfiltertiming = fdmdv.Nfiltertiming;

  % nin  adjust 
  % --------------------------------
  % 120  -1 (one less rate P sample)
  % 160   0 (nominal)
  % 200   1 (one more rate P sample)

  adjust = P - nin*P/M;

  % update buffer of Nt rate P filtered symbols

  rx_filter_mem_timing(:,1:(Nt-1)*P+adjust) = rx_filter_mem_timing(:,P+1-adjust:Nt*P);
  rx_filter_mem_timing(:,(Nt-1)*P+1+adjust:Nt*P) = rx_filt(:,:);

  % sum envelopes of all carriers

  env = sum(abs(rx_filter_mem_timing(:,:))); % use all Nc+1 carriers for timing
  %env = abs(rx_filter_mem_timing(Nc+1,:));  % just use BPSK pilot
  [n m] = size(env);

  % The envelope has a frequency component at the symbol rate.  The
  % phase of this frequency component indicates the timing.  So work out
  % single DFT at frequency 2*pi/P

  x = env * exp(-j*2*pi*(0:m-1)/P)';
  
  norm_rx_timing = angle(x)/(2*pi);
  rx_timing = norm_rx_timing*P + P/4;
  if (rx_timing > P)
     rx_timing -= P;
  end
  if (rx_timing < -P)
     rx_timing += P;
  end

  % rx_filter_mem_timing contains Nt*P samples (Nt symbols at rate P),
  % where Nt is odd.  Lets use linear interpolation to resample in the
  % centre of the timing estimation window

  rx_timing += floor(Nt/2)*P;
  low_sample = floor(rx_timing);
  fract = rx_timing - low_sample;
  high_sample = ceil(rx_timing);
  %printf("rx_timing: %f low_sample: %f high_sample: %f fract: %f\n", rx_timing, low_sample, high_sample, fract);
  
  rx_symbols = rx_filter_mem_timing(:,low_sample)*(1-fract) + rx_filter_mem_timing(:,high_sample)*fract;
  % rx_symbols = rx_filter_mem_timing(:,high_sample+1);

  % This value will be +/- half a symbol so will wrap around at +/-
  % M/2 or +/- 80 samples with M=160

  rx_timing_M = norm_rx_timing*M;

  fdmdv.rx_filter_mem_timing = rx_filter_mem_timing;
endfunction


