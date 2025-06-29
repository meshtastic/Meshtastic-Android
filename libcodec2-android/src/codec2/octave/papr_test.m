% papr_test.m
%
% Experiments with PAPR reduction using clipping/compression
%
% OFDM Tx -> compress -> filter -> normalise power -> channel -> OFDM Rx

#{
  TODO:
  [ ] option for normalised power after clipper
  [ ] experiment to plot those curves
#}

1;

function symbol = qpsk_mod(two_bits)
    two_bits_decimal = sum(two_bits .* [2 1]); 
    switch(two_bits_decimal)
        case (0) symbol =  1;
        case (1) symbol =  j;
        case (2) symbol = -j;
        case (3) symbol = -1;
    endswitch
endfunction

function two_bits = qpsk_demod(symbol)
    bit0 = real(symbol*exp(j*pi/4)) < 0;
    bit1 = imag(symbol*exp(j*pi/4)) < 0;
    two_bits = [bit1 bit0];
endfunction

function papr = calc_papr(tx)
  papr = 10*log10(max(abs(tx).^2)/mean(abs(tx).^2));
end

% test PAPR calculation with a two tone signal of known PAPR (3dB)
function test_papr
  f1=800; f2=1200; Fs=8000; n=(0:Fs-1);
  tx=exp(j*2*pi*n*f1/Fs) + exp(j*2*pi*n*f2/Fs);
  papr = calc_papr(tx);
  assert(abs(papr-3.0) < 0.05, 'test_papr() failed!')
end

% "Genie" OFDM modem simulation that assumes ideal sync

function [ber papr] = run_sim(Nc, Nsym, EbNodB, channel='awgn', plot_en=0, filt_en=0, method="", threshold=1, norm_ebno=0)
    rand('seed',1);
    randn('seed',1);
    
    M    = 160;    % number of samples in each symbol
    bps  = 2;      % two bits per symbol for QPSK
    Ncp  = 16;     % cyclic prefix samples
    Fs   = 8000;
    
    phase_est = 1; % perform phase estimation/correction
    timing = Ncp;

    if strcmp(method,"diversity")
      % total power of tx symbol after combination the same.  Scatter plot positions
      % different but also twice as much noise (bandwidth)
      Nd = 2; gain = 1/sqrt(2);
    else
      Nd = 1; gain = 1.0;
    end
    
    if strcmp(channel,'multipath')
      dopplerSpreadHz = 1; path_delay = Ncp/2;
      Nsam = floor(Nsym*(M+Ncp)*1.1);
      spread1 = doppler_spread(dopplerSpreadHz, Fs, Nsam);
      spread2 = doppler_spread(dopplerSpreadHz, Fs, Nsam);
    end

    papr_log = [];
    for e=1:length(EbNodB)
        % generate a 2D array of QPSK symbols

        Nphases = 2^bps;
        tx_phases = pi/2*floor((rand(Nsym,Nc)*Nphases));
        if strcmp(method,"diversity")
          % duplicate carriers but with opposite phase
          tx_phases = [tx_phases (tx_phases-pi/2)];
        end
        tx_sym = gain*exp(j*tx_phases);

        % carrier frequencies, centre about 0
        st = floor(Nc*Nd/2);
        w = 2*pi/M*(-st:-st+Nc*Nd-1);
        
        % generate OFDM signal

        tx = [];
        for s=1:Nsym
          atx = zeros(1,M);
          for c=1:Nc*Nd
            atx += exp(j*(0:M-1)*w(c))*tx_sym(s,c);
          end
          % insert cyclic prefix and build up stream of time domain symbols
          % note CP costs us 10*log10((Ncp+M)/M) in Eb, as energy in CP isn't used for demodulation
          tx = [tx atx(end-Ncp+1:end) atx];
        end
        Nsam = length(tx);
        
        if strcmp(channel,'multipath')
          assert(length(spread1) >= Nsam);
          assert(length(spread2) >= Nsam);
        end
        
        % bunch of PAPR reduction options
        tx_ = tx;

        % determine threshold based on CDF
        cdf = empirical_cdf((1:Nc),abs(tx));
        if strcmp(method, "clip") || strcmp(method, "diversity") || strcmp(method, "compand")
          if threshold < 1
            threshold_level = find(cdf >= threshold)(1);
          else
            threshold_level = 10*Nc;
          end
          
          % printf("threshold: %f threshold_level: %f\n", threshold, threshold_level);
        end
        
        if strcmp(method, "clip") || strcmp(method, "diversity")
          ind = find(abs(tx) > threshold_level);
          tx_(ind) = threshold_level*exp(j*angle(tx(ind)));
        end
        if strcmp(method, "compand")
          # power law compander x = a*y^power, y = (x/a) ^ (1/power)
          power=2; a=threshold_level/(threshold_level^power);
          tx_mag = (abs(tx)/a) .^ (1/power);
          tx_ = tx_mag.*exp(j*angle(tx));
        end
        
        if filt_en
          Nfilt=80;
          b = fir1(Nfilt,2*Nc*Nd/M);
          tx_ = filter(b,1,[tx_ zeros(1,Nfilt/2)]);
          tx_ = [tx_(Nfilt/2+1:end)];
        end

        rx = tx_;
        
        % multipath channel

        if phase_est
            % estimate phase of each symbol before multipath simulation

            rx_phase1 = zeros(Nsym,Nc);
            for s=1:Nsym
              st = (s-1)*(M+Ncp)+1+timing; en = st+M-1;
              for c=1:Nc*Nd
                rx_phase1(s,c) = sum(exp(-j*(0:M-1)*w(c)) .* rx(st:en))/M;
              end
            end
        end
        
        if strcmp(channel,'multipath')
          rx = spread1(1:Nsam).*rx + spread2(1:Nsam).*[zeros(1,path_delay) rx(1:end-path_delay)];
        end
        
        % normalise power after multipath, so that Eb/No is set up
        % correctly
        
        if norm_ebno == 0
          norm = sqrt(mean(abs(tx_).^2)/mean(abs(rx).^2));
        else
          % normalise after clipper, this makes norm_pwr constant for all test
          % conditions
          norm = sqrt(mean(abs(tx).^2)/mean(abs(rx).^2));
        end
        rx *= norm;
        norm_pwr = 10*log10(mean(abs(rx).^2));
        
        if phase_est
            % auxillary rx to get ideal phase ests on signal after multipath but before AWGN noise is added

            rx_phase = zeros(Nsym,Nc);
            for s=1:Nsym
              st = (s-1)*(M+Ncp)+1+timing; en = st+M-1;
              for c=1:Nc*Nd
                arx_sym = sum(exp(-j*(0:M-1)*w(c)) .* rx(st:en))/M;
                rx_phase(s,c) = arx_sym * conj(rx_phase1(s,c));
              end
            end
            rx_phase = exp(j*arg(rx_phase));
        end

        % AWGN channel

        EsNodB = EbNodB(e) + 10*log10(bps);
        variance = M/(10^(EsNodB/10));
        noise = sqrt(variance/2)*randn(1,Nsam) + j*sqrt(variance/2)*randn(1,Nsam);
        rx += noise;

        % demodulate
        rx_sym = zeros(Nsym,Nc);
        for s=1:Nsym
          st = (s-1)*(M+Ncp)+1+timing; en = st+M-1;
          for c=1:Nc*Nd
            rx_sym(s,c) = sum(exp(-j*(0:M-1)*w(c)) .* rx(st:en))/M;
            if phase_est rx_sym(s,c) *= conj(rx_phase(s,c)); end
          end
          
          if strcmp(method,"diversity")
            for c=1:Nc
              rx_sym(s,c) += rx_sym(s,c+Nc)*exp(j*pi/2);
            end
          end          
        end
        
        % count bit errors

        Tbits = Terrs = 0; ErrPerSym = zeros(1,Nsym);
        for s=1:Nsym
          Nerrs = 0;
          for c=1:Nc
            tx_bits = qpsk_demod(tx_sym(s,c));
            rx_bits = qpsk_demod(rx_sym(s,c));
            Tbits += bps;
            Nerrs += sum(xor(tx_bits,rx_bits));
          end
          ErrPerSym(s) = Nerrs;
          Terrs += Nerrs;
        end

        if plot_en
          figure(1); clf;
          plot(abs(tx(1:5*M))); hold on; plot(abs(tx_(1:5*M))); hold off;
          axis([0 5*M 0 max(abs(tx))]);
          figure(2); clf; [hh nn] = hist(abs(tx),25,1);
          plotyy(nn,hh,1:Nc,cdf); title('PDF and CDF'); grid;
          figure(3); clf; plot(real(rx_sym(:,1:Nc)), imag(rx_sym(:,1:Nc)), '+'); axis([-2 2 -2 2]);
          figure(4); clf; Tx_ = 10*log10(abs(fft(tx_))); plot(fftshift(Tx_));
          mx = 10*ceil(max(Tx_)/10); axis([1 length(Tx_) mx-60 mx]);
          figure(5); plot_specgram(real(rx.*exp(j*2*pi*(0:Nsam-1)/4)));
          figure(6); clf; stem(ErrPerSym);          
        end

        papr1 = calc_papr(tx);
        papr2 = calc_papr(tx_);
        papr_log = [papr_log papr2];
        ber(e) = Terrs/Tbits;
        printf("EbNodB: %4.1f %3.1f %4.1f PAPR: %5.2f %5.2f Tbits: %6d Terrs: %6d BER: %5.3f\n",
               EbNodB(e), norm, norm_pwr, papr1, papr2, Tbits, Terrs, ber(e))
    end

    papr = mean(papr_log);
end

% BER versus Eb/No curves -------------------------------------

% first pass at trying out a few different schemes
function curves_experiment1(Nc=8, channel='awgn', Nsym=1000, EbNodB=2:8)

    [ber1 papr1] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1);
    [ber2 papr2] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1, "clip", threshold=0.8);
    [ber3 papr3] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1, "clip", threshold=0.6);
    [ber4 papr4] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1, "compand", threshold=0.6);
    [ber5 papr5] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1, "diversity", threshold=0.6);

    figure(7); clf;
    semilogy(EbNodB, ber1,sprintf('b+-;vanilla OFDM %3.1f;',papr1),'markersize', 10, 'linewidth', 2); hold on;
    semilogy(EbNodB, ber2,sprintf('r+-;clip 0.8 %3.1f;',papr2),'markersize', 10, 'linewidth', 2); 
    semilogy(EbNodB, ber3,sprintf('g+-;clip 0.6 %3.1f;',papr3),'markersize', 10, 'linewidth', 2);
    semilogy(EbNodB, ber4,sprintf('c+-;compand 0.6 %3.1f;',papr4),'markersize', 10, 'linewidth', 2);
    semilogy(EbNodB, ber5,sprintf('bk+-;diversity 0.6 %3.1f;',papr5),'markersize', 10, 'linewidth', 2);
    hold off;
    axis([min(EbNodB) max(EbNodB) 1E-3 1E-1]); grid;
    xlabel('Eb/No'); title(sprintf("%s Nc = %d", channel, Nc))
    fn = sprintf("papr_exp1_%s_BER_EbNo.png", channel);
    print(fn,"-dpng");

    figure(8); clf;
    semilogy(EbNodB+papr1, ber1,sprintf('b+-;vanilla OFDM %3.1f;',papr1),'markersize', 10, 'linewidth', 2); hold on;
    semilogy(EbNodB+papr2, ber2,sprintf('r+-;clip 0.8 %3.1f;',papr2),'markersize', 10, 'linewidth', 2); 
    semilogy(EbNodB+papr3, ber3,sprintf('g+-;clip 0.6 %3.1f;',papr3),'markersize', 10, 'linewidth', 2);
    semilogy(EbNodB+papr4, ber4,sprintf('c+-;compand 0.6 %3.1f;',papr4),'markersize', 10, 'linewidth', 2);
    semilogy(EbNodB+papr5, ber5,sprintf('bk+-;diversity 0.6 %3.1f;',papr5),'markersize', 10, 'linewidth', 2);
    hold off;
    xlabel('Peak Eb/No');
    axis([min(EbNodB)+papr2 max(EbNodB)+papr1 1E-3 1E-1]); grid; title(sprintf("%s Nc = %d", channel, Nc))
    fn = sprintf("papr_exp1_%s_BER_peakEbNo.png", channel);
    print(fn,"-dpng");
end


% vary threshold and plot BER v Eb/No curves
function curves_experiment2(Nc=8, channel='awgn', Nsym=1000, EbNodB=2:16)

    [ber1 papr1] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1);
    [ber2 papr2] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1, "clip", threshold=0.8);
    [ber3 papr3] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1, "clip", threshold=0.6);
    [ber4 papr4] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1, "clip", threshold=0.4);
    [ber5 papr5] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1, "clip", threshold=0.2);
    [ber6 papr6] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1, "diversity", threshold=0.8);

    figure(7); clf;
    semilogy(EbNodB, ber1,sprintf('b+-;vanilla OFDM %3.1f;',papr1),'markersize', 10, 'linewidth', 2); hold on;
    semilogy(EbNodB, ber2,sprintf('r+-;clip 0.8 %3.1f;',papr2),'markersize', 10, 'linewidth', 2); 
    semilogy(EbNodB, ber3,sprintf('g+-;clip 0.6 %3.1f;',papr3),'markersize', 10, 'linewidth', 2);
    semilogy(EbNodB, ber4,sprintf('c+-;clip 0.4 %3.1f;',papr4),'markersize', 10, 'linewidth', 2);
    semilogy(EbNodB, ber5,sprintf('bk+-;clip 0.2 %3.1f;',papr5),'markersize', 10, 'linewidth', 2);
    semilogy(EbNodB, ber6,sprintf('m+-;diversity 0.8 %3.1f;', papr6),'markersize', 10, 'linewidth', 2);
    hold off;
    axis([min(EbNodB) max(EbNodB) 1E-3 1E-1]); grid;
    xlabel('Eb/No'); title(sprintf("%s Nc = %d", channel, Nc))
    fn = sprintf("papr_exp2_Nc%d_%s_BER_EbNo.png", Nc, channel);
    print(fn,"-dpng");

    figure(8); clf;
    semilogy(EbNodB+papr1, ber1,sprintf('b+-;vanilla OFDM %3.1f;',papr1),'markersize', 10, 'linewidth', 2); hold on;
    semilogy(EbNodB+papr2, ber2,sprintf('r+-;clip 0.8 %3.1f;',papr2),'markersize', 10, 'linewidth', 2); 
    semilogy(EbNodB+papr3, ber3,sprintf('g+-;clip 0.6 %3.1f;',papr3),'markersize', 10, 'linewidth', 2);
    semilogy(EbNodB+papr4, ber4,sprintf('c+-;clip 0.4 %3.1f;',papr4),'markersize', 10, 'linewidth', 2);
    semilogy(EbNodB+papr5, ber5,sprintf('bk+-;clip 0.2 %3.1f;',papr5),'markersize', 10, 'linewidth', 2);
    semilogy(EbNodB+papr6, ber6,sprintf('m+-;diversity 0.8 %3.1f;', papr6),'markersize', 10, 'linewidth', 2);
    hold off;
    xlabel('Peak Eb/No');
    axis([min(EbNodB)+papr2 max(EbNodB)+papr1 1E-3 1E-1]); grid; title(sprintf("%s Nc = %d", channel, Nc))
    fn = sprintf("papr_exp2_Nc%d_%s_BER_peakEbNo.png", Nc, channel);
    print(fn,"-dpng");
end

% PAPR against number of carriers Nc
function curves_experiment3(Nsym=3000)

    paper = zeros(1,32);
    Nc = 2:2:32;
    for i = 1:length(Nc)
      aNc = Nc(i);
      [aber apapr] = run_sim(aNc, Nsym, 100);
      papr(aNc) = apapr;
    end  

    figure(9); clf;
    plot(Nc, papr(Nc)); xlabel('Number of Carriers Nc'); ylabel('PAPR (dB)'); grid;
    fn = sprintf("papr_exp3_Nc.png");
    print(fn,"-dpng");
end

% focus on diversity - vary threshold and plot BER v Eb/No curves
function curves_experiment4(Nc=8, channel='multipath', Nsym=3000, EbNodB=2:2:16)

    [ber1 papr1] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1);
    [ber2 papr2] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1, "diversity", threshold=1);
    [ber3 papr3] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1, "diversity", threshold=0.8);
    [ber4 papr4] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1, "diversity", threshold=0.6);

    figure(7); clf;
    semilogy(EbNodB, ber1,sprintf('b+-;vanilla OFDM %3.1f;',papr1),'markersize', 10, 'linewidth', 2); hold on;
    semilogy(EbNodB, ber2,sprintf('r+-;diversity 1.0 %3.1f;',papr2),'markersize', 10, 'linewidth', 2); 
    semilogy(EbNodB, ber3,sprintf('g+-;diversity 0.8 %3.1f;',papr3),'markersize', 10, 'linewidth', 2);
    semilogy(EbNodB, ber4,sprintf('c+-;diversity 0.6 %3.1f;',papr4),'markersize', 10, 'linewidth', 2);
    hold off;
    axis([min(EbNodB) max(EbNodB) 1E-3 1E-1]); grid;
    xlabel('Eb/No'); title(sprintf("%s Nc = %d", channel, Nc))
    fn = sprintf("papr_exp4_Nc%d_%s_BER_EbNo.png", Nc, channel);
    print(fn,"-dpng");

    figure(8); clf;
    semilogy(EbNodB+papr1, ber1,sprintf('b+-;vanilla OFDM %3.1f;',papr1),'markersize', 10, 'linewidth', 2); hold on;
    semilogy(EbNodB+papr2, ber2,sprintf('r+-;diversity 1.0 %3.1f;',papr2),'markersize', 10, 'linewidth', 2); 
    semilogy(EbNodB+papr3, ber3,sprintf('g+-;diversity 0.8 %3.1f;',papr3),'markersize', 10, 'linewidth', 2);
    semilogy(EbNodB+papr4, ber4,sprintf('c+-;diversity 0.6 %3.1f;',papr4),'markersize', 10, 'linewidth', 2);
    hold off;
    xlabel('Peak Eb/No');
    axis([min(EbNodB)+papr4 max(EbNodB)+papr1 1E-3 1E-1]); grid; title(sprintf("%s Nc = %d", channel, Nc))
    fn = sprintf("papr_exp4_Nc%d_%s_BER_peakEbNo.png", Nc, channel);
    print(fn,"-dpng");
end

% plot BER v Eb/No curves for clipping with normalised Eb/No after clipping
function curves_experiment5(Nc=8, channel='awgn', Nsym=1000, EbNodB=2:10)

    [ber1 papr1] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1, "", threshold=1, norm=1);
    [ber2 papr2] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1, "clip", threshold=0.8, norm=1);
    [ber3 papr3] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1, "clip", threshold=0.6, norm=1);
    [ber4 papr4] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1, "clip", threshold=0.4, norm=1);
    [ber5 papr5] = run_sim(Nc, Nsym, EbNodB, channel, 0, filt_en=1, "clip", threshold=0.2, norm=1);

    figure(7); clf;
    semilogy(EbNodB, ber1,sprintf('b+-;vanilla OFDM %3.1f;',papr1),'markersize', 10, 'linewidth', 2); hold on;
    semilogy(EbNodB, ber2,sprintf('r+-;clip 0.8 %3.1f;',papr2),'markersize', 10, 'linewidth', 2); 
    semilogy(EbNodB, ber3,sprintf('g+-;clip 0.6 %3.1f;',papr3),'markersize', 10, 'linewidth', 2);
    semilogy(EbNodB, ber4,sprintf('c+-;clip 0.4 %3.1f;',papr4),'markersize', 10, 'linewidth', 2);
    semilogy(EbNodB, ber5,sprintf('bk+-;clip 0.2 %3.1f;',papr5),'markersize', 10, 'linewidth', 2);
    hold off;
    axis([min(EbNodB) max(EbNodB) 1E-3 1E-1]); grid;
    xlabel('Eb/No'); title(sprintf("%s Nc = %d", channel, Nc))
    fn = sprintf("papr_exp5_Nc%d_%s_BER_EbNo.png", Nc, channel);
    print(fn,"-dpng");
end

pkg load statistics;
more off;

test_papr;

% single point with lots of plots -----------

%run_sim(8, 1000, EbNo=100, channel='awgn', plot_en=1, filt_en=1);
%run_sim(8, 8, EbNo=100, channel='awgn', plot_en=1, filt_en=1, "diversity", threshold=0.8);
%run_sim(8, 1000, EbNo=10, channel='multipath', plot_en=1, filt_en=0, "diversity", threshold=5);
%curves_experiment2(Nc=16, 'awgn', Nsym=1000);
curves_experiment2(Nc=16,'multipath', Nsym=3000, EbNodB=2:2:16);
%curves_experiment3()
%curves_experiment4()
%curves_experiment5(Nc=16)
