% fsk_lock_down.m
% David Rowe April 2020
%
% tests for "Lock down" waveform, low Eb/No 4FSK

fsk_lib;

% "Genie" (ie perfect) timing estimate, we just want to see impact on BER from frequency est errors
function rx_bits = simple_fsk_demod(states, rx, f)
  M = states.M; Ts = states.Ts; Fs = states.Fs; N = states.nin;

  nsymb = states.nin/states.Ts;
  rx_filter = zeros(states.nin,M);
  rx_symbols = zeros(nsymb,M);

  % Down convert each tone. We can use any time index for down
  % conversion as it's non-coherent
  for m=1:M
     phase = exp(-j*2*pi*(1:N)'*f(m)/Fs);
     rx_filter(:,m) = rx .* phase;
  end

  % sum energy for each symbol
  for s=1:nsymb
    st_sym = (s-1)*Ts+1; en_sym = s*Ts;
    for m=1:M
      rx_symbols(s,m) = sum(rx_filter(st_sym:en_sym,m));
    end
  end

  % map symbols back to bits
  rx_bits = [];
  for s=1:nsymb
    [tone_max tone_index] = max(rx_symbols(s,:));
    arx_bits = dec2bin(tone_index - 1, states.bitspersymbol) - '0';
    rx_bits = [rx_bits arx_bits];
  end
  
end

% set up "lock down" waveform
function [states M bits_per_frame] = lock_down_init(Rs,Fs,df,tx_tone_separation=250)
  M  = 4;
  states = fsk_init(Fs,Rs,M,P=8,nsym=100);
  bits_per_frame = 512;
  states.tx_real = 0; % complex signal
  states.tx_tone_separation = tx_tone_separation;
  states.ftx = -2.5*states.tx_tone_separation + states.tx_tone_separation*(1:M);
  states.fest_fmin = -Fs/2;
  states.fest_fmax = +Fs/2;
  states.df = df;

  % cumulative PDF, cdf(x) probability of 0....x errors in frame
  cdf = binocdf(1:bits_per_frame, bits_per_frame, 0.3);
  % our valid frame threshold is 50% probability, so if we get this many errors
  % we have a 50% chance it's a valid frame
  nerrs_valid = find(cdf>=0.5)(1);
  % our invalid frame threshold is 99% probability, so very unlikley to
  % get this many errors
  nerrs_invalid =  find(cdf>=0.99)(1);
  states.ber_valid_thresh = nerrs_valid/bits_per_frame;
  states.ber_invalid_thresh = nerrs_invalid/bits_per_frame; 
end

% run a test at an Eb/No point, measure how many dud freq estimates using both algorithms
function [states f_log f_log2 num_dud1 num_dud2 ber ber2] = freq_run_test(EbNodB = 10, num_frames=10, Fs=8000, Rs=100, df=0)
  [states M bits_per_frame] = lock_down_init(Rs,Fs,df);
  N = states.N;
  
  EbNo = 10^(EbNodB/10);
  variance = states.Fs/(states.Rs*EbNo*states.bitspersymbol);

  nbits = bits_per_frame*num_frames;
  tx_bits = round(rand(1,nbits));
  tx = fsk_mod(states, tx_bits);
  noise = sqrt(variance/2)*randn(length(tx),1) + j*sqrt(variance/2)*randn(length(tx),1);
  rx = tx + noise;
  run_frames = floor(length(rx)/N)-1;
  st = 1; f_log = []; f_log2 = []; rx_bits = []; rx_bits2 = [];
  for f=1:run_frames

    % extract nin samples from input stream
    nin = states.nin;
    en = st + states.nin - 1;

    % due to nin variations it's possible to overrun buffer
    if en < length(rx)
      sf = rx(st:en);
      states = est_freq(states, sf, states.M);
      arx_bits = simple_fsk_demod(states, sf, states.f);
      rx_bits = [rx_bits arx_bits];
      arx_bits = simple_fsk_demod(states, sf, states.f2);
      rx_bits2 = [rx_bits2 arx_bits];
      f_log = [f_log; states.f]; f_log2 = [f_log2; states.f2];
      st += nin;
    end
  end

  % ignore start up transient
  startup = 1; % TODO make this sensible/proportional so its scales across Rs
  if num_frames > startup
    tx_bits = tx_bits(startup*bits_per_frame:end);
    rx_bits = rx_bits(startup*bits_per_frame:end);
    rx_bits2 = rx_bits2(startup*bits_per_frame:end);
  end
  
  % measure BER
  nerrors = sum(xor(tx_bits(1:length(rx_bits)),rx_bits)); ber = nerrors/nbits;
  nerrors2 = sum(xor(tx_bits(1:length(rx_bits2)),rx_bits2)); ber2 = nerrors2/nbits;
  
  % Lets say that for a valid freq estimate, all four tones must be within 0.1*Rs of their tx freqeuncy
  num_dud1 = 0; num_dud2 = 0;
  for i=1:length(f_log)
    if sum(abs(f_log(i,:)-states.ftx) > 0.1*states.Rs)
      num_dud1++;
    end
    if sum(abs(f_log2(i,:)-states.ftx) > 0.1*states.Rs)
      num_dud2++;
    end
  end
end

function freq_run_single(EbNodB = 3, num_frames = 10)
  [states f_log f_log2 num_dud1 num_dud2 ber ber2] = freq_run_test(EbNodB, num_frames);
  
  percent_dud1 = 100*num_dud1/length(f_log);
  percent_dud2 = 100*num_dud2/length(f_log);
  printf("EbNodB: %4.2f dB tests: %3d duds1: %3d %5.2f %% duds2: %3d %5.2f %% ber1: %4.3f ber2: %4.3f\n",
         EbNodB, length(f_log), num_dud1, percent_dud1, num_dud2, percent_dud2, ber, ber2)
  
  figure(1); clf;
  ideal=ones(length(f_log),1)*states.ftx;
  plot((1:length(f_log)),ideal(:,1),'bk;ideal;')
  hold on; plot((1:length(f_log)),ideal(:,2:states.M),'bk'); hold off;
  hold on;
  plot(f_log(:,1), 'linewidth', 2, 'b;peak;');
  plot(f_log(:,2:states.M), 'linewidth', 2, 'b');
  plot(f_log2(:,1),'linewidth', 2, 'r;mask;');
  plot(f_log2(:,2:states.M),'linewidth', 2, 'r');
  hold off;
  xlabel('Time (frames)'); ylabel('Frequency (Hz)');
  title(sprintf("EbNo = %4.2f dB", EbNodB));
  print("fsk_freq_est_single.png", "-dpng")

  figure(2); clf;
  errors = (f_log - states.ftx)(:);
  ind = find(abs(errors) < 100);
  errors2 = (f_log2 - states.ftx)(:);
  ind2 = find(abs(errors2) < 100);
  if length(ind)
    subplot(211); hist(errors(ind),50)
  end
  if length(ind2)
    subplot(212); hist(errors2(ind2),50)
  end
end


% test peak and mask algorthms side by side
function freq_run_curve_peak_mask

   EbNodB = 0:9;
   m4fsk_ber_theory = [0.23 0.18 0.14 0.09772 0.06156 0.03395 0.01579 0.00591 0.00168 3.39E-4];
   percent_log = []; ber_log = [];
   for ne = 1:length(EbNodB)
      [states f_log f_log2 num_dud1 num_dud2 ber ber2] = freq_run_test(EbNodB(ne), 10);
      percent_dud1 = 100*num_dud1/length(f_log);
      percent_dud2 = 100*num_dud2/length(f_log);
      percent_log = [percent_log; [percent_dud1 percent_dud2]];
      ber_log = [ber_log; [ber ber2]];
      printf("EbNodB: %4.2f dB tests: %3d duds1: %3d %5.2f %% duds2: %3d %5.2f %% ber1: %4.3f ber2: %4.3f\n",
             EbNodB(ne), length(f_log), num_dud1, percent_dud1, num_dud2, percent_dud2, ber, ber2)
  end
  
  figure(1); clf; plot(EbNodB, percent_log(:,1), 'linewidth', 2, '+-;peak;'); grid;
  hold on;  plot(EbNodB, percent_log(:,2), 'linewidth', 2, 'r+-;mask;'); hold off;
  xlabel('Eb/No (dB)'); ylabel('% Errors');
  title(sprintf("Fs = %d Rs = %d df = %3.2f", states.Fs, states.Rs, states.df));
  print("fsk_freq_est_errors.png", "-dpng")

  figure(2); clf; semilogy(EbNodB, m4fsk_ber_theory, 'linewidth', 2, 'bk+-;theory;'); grid;
  hold on;  semilogy(EbNodB, ber_log(:,1), 'linewidth', 2, '+-;peak;');
  semilogy(EbNodB, ber_log(:,2), 'linewidth', 2, 'r+-;mask;'); hold off;
  xlabel('Eb/No (dB)'); ylabel('BER');
  title(sprintf("Fs = %d Rs = %d df = %3.2f", states.Fs, states.Rs, states.df));
  print("fsk_freq_est_ber.png", "-dpng")
end


function freq_run_curve_mask(Fs,Rs)
  EbNodB = 0:9;
  m4fsk_ber_theory = [0.23 0.18 0.14 0.09772 0.06156 0.03395 0.01579 0.00591 0.00168 3.39E-4];
  figure(1); clf; semilogy(EbNodB, m4fsk_ber_theory, 'linewidth', 2, 'bk+-;theory;'); grid;
  xlabel('Eb/No (dB)'); ylabel('BER');
  title(sprintf("Mask: Fs = %d Hz Rs = %d Hz", Fs, Rs));
  hold on;
   
  for df=-0.01:0.01:0.01
    ber_log = [];
    for ne = 1:length(EbNodB)
      [states f_log f_log2 num_dud1 num_dud2 ber ber2] = freq_run_test(EbNodB(ne), 100, Fs, Rs, df*Rs);
      ber_log = [ber_log; [ber ber2]];
      printf("Fs: %d Rs: %d df %3.2f EbNodB: %4.2f dB tests: %3d ber: %4.3f\n",
             Fs, Rs, df, EbNodB(ne), length(f_log), ber2)
    end 
    semilogy(EbNodB, ber_log(:,2), 'linewidth', 2, sprintf("+-;df=% 3.2f Hz/s;",df*Rs));
  end
  hold off;
  print(sprintf("fsk_freq_est_ber_%d_%d.png",Fs,Rs), "-dpng")
end


% Run a complete modem (freq and timing estimators running) at a
% single Eb/No point.  At low Eb/No the estimators occasionally fall
% over so we get complete junk, we consider that case a packet error
% and exclude it from the BER estimation.

function [states ber per] = modem_run_test(EbNodB = 10, num_frames=10, Fs=8000, Rs=100, df=0, plots=0, spreadHz=0,tx_tone_separation=250)
  [states M bits_per_frame] = lock_down_init(Rs, Fs, df, tx_tone_separation);
  N = states.N;
  if plots; states.verbose = 0x4; end
  EbNo = 10^(EbNodB/10);
  variance = states.Fs/(states.Rs*EbNo*states.bitspersymbol);

  nbits = bits_per_frame*num_frames;
  test_frame = round(rand(1,bits_per_frame)); tx_bits = [];
  for f=1:num_frames
    tx_bits = [tx_bits test_frame];
  end

  tx = fsk_mod(states, tx_bits);
  noise = sqrt(variance/2)*randn(length(tx),1) + j*sqrt(variance/2)*randn(length(tx),1);
  if spreadHz
    % just use phase part of doppler spread, not interested in amplitude fading
    spread = doppler_spread(spreadHz, Fs, round(1.1*length(tx)));
    spread = exp(j*arg(spread(1:length(tx))));
    rx = tx.*rot90(spread) + noise;
  else
    rx = tx + noise;
  end
  run_frames = floor(length(rx)/N)-1;
  st = 1; f_log = []; f_log2 = []; rx_bits = []; rx_bits2 = [];
  for f=1:run_frames

    % extract nin samples from input stream
    nin = states.nin;
    en = st + states.nin - 1;

    % due to nin variations it's possible to overrun buffer
    if en < length(rx)
      sf = rx(st:en);
      states = est_freq(states, sf, states.M);  states.f = states.f2;
      [arx_bits states] = fsk_demod(states, sf);
      rx_bits = [rx_bits arx_bits];
      f_log = [f_log; states.f];
      st += nin;
    end
  end

  num_frames=floor(length(rx_bits)/bits_per_frame);
  log_nerrs = []; num_frames_rx = 0;
  for f=1:num_frames-1
    st = (f-1)*bits_per_frame + 1; en = (f+1)*bits_per_frame;
    states = ber_counter(states, test_frame, rx_bits(st:en));
    log_nerrs = [log_nerrs states.nerr];
    if states.ber_state; num_frames_rx++; end
  end
  if states.Terrs
    printf("Fs: %d Rs: %d df % 3.2f sp: %2.1f EbNo: %4.2f ftx: %3d frx: %3d nbits: %4d nerrs: %3d ber: %4.3f\n",
            Fs, Rs, df, spreadHz, EbNodB, num_frames, num_frames_rx, states.Tbits, states.Terrs, states.Terrs/states.Tbits);
    ber = states.Terrs/states.Tbits;
  else
    ber = 0.5;
  end

  if plots
    figure(1); clf;
    ideal=ones(length(f_log),1)*states.ftx;
    plot((1:length(f_log)),ideal(:,1),'bk;ideal;')
    hold on; plot((1:length(f_log)),ideal(:,2:states.M),'bk'); hold off;
    hold on;
    plot(f_log(:,1), 'linewidth', 2, 'b;peak;');
    plot(f_log(:,2:states.M), 'linewidth', 2, 'b');
    hold off;
    xlabel('Time (frames)'); ylabel('Frequency (Hz)');
    figure(2); clf; plot(log_nerrs); title('Errors per frame');
  end

  per = 1 - num_frames_rx/num_frames;
end


% run BER v Eb/No curves over a range of frequency rate/change
function modem_run_curve(Fs, Rs, num_frames=100, dfmax=0.01)
  EbNodB = 0:9;
  m4fsk_ber_theory = [0.23 0.18 0.14 0.09772 0.06156 0.03395 0.01579 0.00591 0.00168 3.39E-4];
  figure(1); clf; semilogy(EbNodB, m4fsk_ber_theory, 'linewidth', 2, 'bk+-;theory;'); grid;
  xlabel('Eb/No (dB)'); ylabel('BER');
  title(sprintf("Mask: Fs = %d Hz Rs = %d Hz", Fs, Rs)); hold on;
  figure(2); clf;
  xlabel('Eb/No (dB)'); ylabel('PER'); title(sprintf("Mask: Fs = %d Hz Rs = %d Hz", Fs, Rs));
  grid; axis([min(EbNodB) max(EbNodB) 0 1]); hold on;
  
  for df=-dfmax:dfmax:dfmax
    ber_log = []; per_log = [];
    for ne = 1:length(EbNodB)
      [states ber per] = modem_run_test(EbNodB(ne), num_frames, Fs, Rs, df*Rs);
      ber_log = [ber_log; ber]; per_log = [per_log; per];
    end 
    figure(1); semilogy(EbNodB, ber_log, 'linewidth', 2, sprintf("+-;df=% 3.2f Hz/s;",df*Rs));
    figure(2); plot(EbNodB, per_log, 'linewidth', 2, sprintf("+-;df=% 3.2f Hz/s;",df*Rs));
  end

  figure(1); hold off; print(sprintf("fsk_modem_ber_%d_%d.png",Fs,Rs), "-dpng")
  figure(2); hold off; print(sprintf("fsk_modem_per_%d_%d.png",Fs,Rs), "-dpng")
end

% run BER v Eb/No curve with some phase noise spreading the energy of the tones in frequency
function modem_run_curve_spread(Fs, Rs, num_frames=100)
  EbNodB = 0:9;
  m4fsk_ber_theory = [0.23 0.18 0.14 0.09772 0.06156 0.03395 0.01579 0.00591 0.00168 3.39E-4];
  figure(1); clf; semilogy(EbNodB, m4fsk_ber_theory, 'linewidth', 2, 'bk+-;theory;'); grid;
  xlabel('Eb/No (dB)'); ylabel('BER');
  title(sprintf("Spread: Fs = %d Hz Rs = %d Hz", Fs, Rs)); hold on;
  figure(2); clf;
  xlabel('Eb/No (dB)'); ylabel('PER');
  title(sprintf("Spread: Fs = %d Hz Rs = %d Hz", Fs, Rs));
  grid; axis([min(EbNodB) max(EbNodB) 0 1]); hold on;

  spreadHz = [0.0 1 2 5];
  for ns = 1:length(spreadHz)
    ber_log = []; per_log = [];
    for ne = 1:length(EbNodB)
      [states ber per] = modem_run_test(EbNodB(ne), num_frames, Fs, Rs, 0, 0, spreadHz(ns));
      ber_log = [ber_log; ber]; per_log = [per_log; per];
    end 
    figure(1); semilogy(EbNodB, ber_log, 'linewidth', 2, sprintf("+-;spread=% 3.2f Hz;",spreadHz(ns)));
    figure(2); plot(EbNodB, per_log, 'linewidth', 2, sprintf("+-;spread=% 3.2f Hz;",spreadHz(ns)));
  end
  
  figure(1); hold off; print(sprintf("fsk_modem_ber_spread_%d_%d.png",Fs,Rs), "-dpng")
  figure(2); hold off; print(sprintf("fsk_modem_per_spread_%d_%d.png",Fs,Rs), "-dpng")
end

% study code rate versus Rs and MDS
function code_rate_table
  packet_duration_sec = 20;
  k = 256;
  noise_figure = 1;
  bits_per_symbol = 2;
  noise_bandwidth = 3000;
  
  code_rate=[1 0.8 0.5 1/3];
  raw_ber=[2E-3 0.04 0.08 0.16];
  EbNodB_4fsk=[8 4.5 3.5 1.5];

  printf("Code Rate | Raw BER | 4FSK Eb/No | n,k | Rs | SNR | MDS |\n");
  printf("| --- | --- | --- | --- | --- | --- | --- |\n");
  for i=1:length(code_rate)
    n = k/code_rate(i);
    Rb = n/packet_duration_sec;
    Rs = Rb/bits_per_symbol;
    snr = EbNodB_4fsk(i) + 10*log10(Rb/noise_bandwidth);
    mds = EbNodB_4fsk(i) + 10*log10(Rb) + noise_figure - 174; 
    printf("%3.2f | %4.3f | %2.1f | %d,%d | %4.1f | %4.1f | %5.1f |\n",
    code_rate(i), raw_ber(i), EbNodB_4fsk(i), n, k, Rs, snr, mds);
  end
end

graphics_toolkit("gnuplot");
more off;

% same results every time
rand('state',1); 
randn('state',1);

% freq estimator tests (choose one)
#freq_run_single(3,10)
#freq_run_curve_peak_mask
#freq_run_curve_mask(8000,100)
#freq_run_curve_mask(24000,25)
#freq_run_curve_mask(8000,25)

% complete modem tests (choose one)
#modem_run_curve(24000,25,100)
#modem_run_curve(8000,100,50,0.05)
#modem_run_curve_spread(8000,25,50)
#modem_run_curve(8000,100,20)
modem_run_test(2, 20, 8000, 25, 0, 1, 0, 270);

% just print a table of code rates
#code_rate_table

