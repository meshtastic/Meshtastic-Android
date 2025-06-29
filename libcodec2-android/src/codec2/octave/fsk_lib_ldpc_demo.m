% fsk_lib_ldpc_demo.m
%
% LDPC coded 4FSK modem demo, demonstrating soft dec using CML library functions

fsk_lib;
ldpc;

% set up waveform
function [states M] = modem_init(Rs,Fs,df)
  M  = 4;
  states = fsk_init(Fs,Rs,M,P=8,nsym=100);
  states.tx_real = 0;
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

function [states uber cber cper] = modem_run_test(HRA, EbNodB = 10, num_frames=10, Fs=8000, Rs=100, df=0, plots=0)
  rand('seed',1); randn('seed',1);
  [states M] = modem_init(Rs, Fs, df);
  N = states.N;
  if plots; states.verbose = 0x4; end

  % set up LDPC code
  Hsize=size(HRA);
  Krate = (Hsize(2)-Hsize(1))/Hsize(2); states.rate = Krate;
  code_param = ldpc_init_user(HRA, modulation='FSK', mod_order=states.M, mapping='gray');
  states.coden = code_param.coded_bits_per_frame;
  states.codek = code_param.data_bits_per_frame;

  % set up AWGN noise
  EcNodB = EbNodB + 10*log10(Krate);
  EcNo = 10^(EcNodB/10);
  variance = states.Fs/(states.Rs*EcNo*states.bitspersymbol);

  data_bits = round(rand(1,code_param.data_bits_per_frame)); tx_bits = [];
  for f=1:num_frames
    codeword_bits = LdpcEncode(data_bits, code_param.H_rows, code_param.P_matrix);
    tx_bits = [tx_bits codeword_bits];
  end

  % modulator and AWGN channel
  tx = fsk_mod(states, tx_bits);
  noise = sqrt(variance/2)*randn(length(tx),1) + j*sqrt(variance/2)*randn(length(tx),1);
  rx = tx + noise;

  % freq estimator and demod
  run_frames = floor(length(rx)/N)-1;
  st = 1; f_log = []; rx_bits = []; rx_filt = [];
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
      rx_filt = [rx_filt abs(states.f_int_resample)];
      f_log = [f_log; states.f];
      st += nin;
    end
  end

  % count bit errors in test frames

  num_frames=floor(length(rx_bits)/code_param.coded_bits_per_frame);
  log_nerrs = []; num_frames_rx = 0; Tbits = Terrs = Tperr = Tpackets = 0;
  uber = cber = 0.5; cper = 1;
  for f=1:num_frames-1
    st = (f-1)*code_param.coded_bits_per_frame + 1; en = (f+1)*code_param.coded_bits_per_frame;
    states = ber_counter(states, codeword_bits, rx_bits(st:en));
    log_nerrs = [log_nerrs states.nerr];
    if states.ber_state num_frames_rx++; end

    % Using sync provided by ber_counter() state machine for LDPC frame alignment
    if states.ber_state
      st_bit = (f-1)*code_param.coded_bits_per_frame + states.coarse_offset;
      st_symbol = (st_bit-1)/states.bitspersymbol + 1;
      en_symbol = st_symbol +  code_param.coded_bits_per_frame/states.bitspersymbol - 1;
      %printf("coded_bits: %d bps: %d st_bit: %d st_symbol: %d en_symbol: %d\n",
      %code_param.coded_bits_per_frame, states.bitspersymbol, st_bit,  st_symbol, en_symbol);

      % map FSK filter ouputs to LLRs, then LDPC decode (see also fsk_cml_sam.m)
      symL = DemodFSK(1/states.v_est*rx_filt(:,st_symbol:en_symbol), states.SNRest, 1);
      llr = -Somap(symL);
      [x_hat, PCcnt] = MpDecode(llr, code_param.H_rows, code_param.H_cols, max_iterations=100, decoder_type=0, 1, 1);
      Niters = sum(PCcnt~=0);
      detected_data = x_hat(Niters,:);
      Nerrs = sum(xor(data_bits, detected_data(1:code_param.data_bits_per_frame)));
      Terrs += Nerrs;
      Tbits += code_param.data_bits_per_frame;
      if Nerrs Tperr++; end
      Tpackets++;
    end
  end

  if states.Terrs
    printf("Fs: %d Rs: %d df % 3.2f EbNo: %4.2f ftx: %3d frx: %3d\n",Fs, Rs, df, EbNodB, num_frames, num_frames_rx);
    uber = states.Terrs/states.Tbits; cber = Terrs/Tbits; cper = Tperr/Tpackets;
    printf("  Uncoded: nbits: %6d nerrs: %6d ber: %4.3f\n", states.Tbits, states.Terrs, uber);
    printf("  Coded..: nbits: %6d nerrs: %6d ber: %4.3f\n", Tbits, Terrs, cber);
    printf("  Coded..: npckt: %6d perrs: %6d per: %4.3f\n", Tpackets, Tperr, cper);
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

end


function freq_run_curve_peak_mask(HRA, num_frames=100)

  EbNodB = 4:10;
  m4fsk_ber_theory = [0.23 0.18 0.14 0.09772 0.06156 0.03395 0.01579 0.00591 0.00168 3.39E-4];
  uber_log = []; cber_log = []; cper_log = [];
  for ne = 1:length(EbNodB)
    [states uber cber cper] = modem_run_test(HRA, EbNodB(ne), num_frames);
    uber_log = [uber_log uber];  cber_log = [cber_log cber];  cper_log = [cper_log cper];
  end

  figure(1); clf;
  EbNodB_raw = EbNodB+10*log10(states.rate)
  semilogy(EbNodB_raw, m4fsk_ber_theory(round(EbNodB_raw+1)), 'linewidth', 2, 'bk+-;uber theory;');
  grid; hold on;
  semilogy(EbNodB_raw, uber_log+1E-12, 'linewidth', 2, '+-;uber;');
  semilogy(EbNodB, cber_log+1E-12, 'linewidth', 2, 'r+-;cber;');
  semilogy(EbNodB, cper_log+1E-12, 'linewidth', 2, 'c+-;cper;'); hold off;
  xlabel('Eb/No (info bits, dB)'); ylabel('BER/PER'); axis([min(EbNodB_raw) max(EbNodB) 1E-4 1]);
  title(sprintf("%dFSK rate %3.1f (%d,%d) Ncodewords=%d NCodewordBits=%d Fs=%d Rs=%d",
                states.M, states.rate, states.coden, states.codek, num_frames, states.Tbits, states.Fs, states.Rs));
  print("fsk_lib_ldpc.png", "-dpng")
end

% Choose simulation here ---------------------------------------------------

init_cml();
load H_256_512_4.mat; HRA=H;
more off;

% single point
[states uber cber cper] = modem_run_test(HRA, EbNodB=8);
if cber == 0
  printf("PASS\n");
end

% curve
%freq_run_curve_peak_mask(HRA, 200)
