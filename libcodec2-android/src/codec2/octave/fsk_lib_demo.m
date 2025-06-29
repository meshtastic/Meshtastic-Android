% fsk_lib_demo.m
% Uncoded FSK modem demo

fsk_lib;

% set up waveform
function [states M bits_per_frame] = modem_init(Rs,Fs,df)
  M  = 4;
  states = fsk_init(Fs,Rs,M,P=8,nsym=100);
  bits_per_frame = 512;
  states.tx_real = 0; % complex signal
  states.tx_tone_separation = 250;
  states.ftx = -2.5*states.tx_tone_separation + states.tx_tone_separation*(1:M);
  states.fest_fmin = -Fs/2;
  states.fest_fmax = +Fs/2;
  states.fest_min_spacing = Rs/2;
  states.df = df;

  states.ber_valid_thresh = 0.1;
  states.ber_invalid_thresh = 0.2; 
end

% Run a complete modem (freq and timing estimators running) at a
% single Eb/No point.  At low Eb/No the estimators occasionally fall
% over so we get complete junk, we consider that case a packet error
% and exclude it from the BER estimation.

function [states ber per] = modem_run_test(EbNodB = 10, num_frames=10, Fs=8000, Rs=100, df=0, plots=0)
  randn('state',1); rand('state',1);
  [states M bits_per_frame] = modem_init(Rs, Fs, df);
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
    printf("Fs: %d Rs: %d df % 3.2f EbNo: %4.2f ftx: %3d frx: %3d nbits: %4d nerrs: %3d ber: %4.3f\n",
            Fs, Rs, df, EbNodB, num_frames, num_frames_rx, states.Tbits, states.Terrs, states.Terrs/states.Tbits);
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

[states ber per] = modem_run_test(EbNodB=6);
BER_theory=0.01579; % for Eb/No = 6dB
if ber < 1.5*BER_theory
  printf("PASS\n");
end
