% cma.m
%
% Constant modulus equaliser example from:
%
% http://dsp.stackexchange.com/questions/23540/matlab-proper-estimation-of-weights-and-how-to-calculate-mse-for-qpsk-signal-f
%
% Adapted to run bpsk and fsk signals

    rand('seed',1);
    randn('seed',1);

    N = 5000;           % # symbols
    h = [1 0 0 0 0 0 0.0 0.5];  % simulation of HF multipath channel impulse response
    h = h/norm(h);
    Le = 20;            % equalizer length
    mu = 1E-3;          % step size
    snr = 30;           % snr in dB
    M = 10;             % oversample rate, e.g. Rs=400Hz at Fs=8000Hz

    tx_type = "fsk";   % select modulation type here "bpsk" or "fsk"

    if strcmp(tx_type, "bpsk")
      s0 = round( rand(N,1) )*2 - 1;     % BPSK signal
      s0M = zeros(N*M,1);                % oversampled BPSK signal
      k = 1;
      for i=1:M:N*M
       s0M(i:i+M-1) = s0(k);
        k ++;
      end
    end

    if strcmp(tx_type, "fsk")
      tx_bits = round(rand(1,N));

      % continuous phase FSK modulator

      w1 = pi/4;
      w2 = pi/2;
      tx_phase = 0;
      tx = zeros(M*N,1);

      for i=1:N
        for k=1:M
          if tx_bits(i)
            tx_phase += w2;
          else
            tx_phase += w1;
          end
          tx((i-1)*M+k) = exp(j*tx_phase);
        end
      end

      s0M = tx;
    end

    s = filter(h,1,s0M);                % filtered signal

    % add Gaussian noise at desired snr

    n = randn(N*M,1);
    vs = var(s);
    vn = vs*10^(-snr/10);
    n = sqrt(vn)*n;
    r = s + n;          % received signal

    e = zeros(N*M,1);   % error
    w = zeros(Le,1);    % equalizer coefficients
    w(Le)=1;            % actual filter taps are flipud(w)!

    yd = zeros(N*M,1);

    for i = 1:N*M-Le,
        x = r(i:Le+i-1);
        y = w'*x;
        yd(i)=y;
        e(i) = abs(y).^2 - 1;
        w = w - mu * e(i) * real(conj(y) * x);
    end

    np = 100;           % # sybmols to plot (last np will be plotted); np < N!

    figure(1); clf;
    %subplot(211), plot( 1:np, e(N-np+1-Le+1:N-Le+1).*e(N-np+1-Le+1:N-Le+1)), title('error')
    subplot(211), plot(e.*e), title('error');
    subplot(212), stem(conv(flipud(w),h)), title('equalized channel impulse response')

    figure(2); clf;
    subplot(311)
    plot(1:np, s0M(N-np+1:N))
    title('transmitted, received, and equalized signal')
    subplot(312)
    plot(1:np, r(N-np+1:N))
    subplot(313)
    plot(1:np, yd(N-np+1-Le+1:N-Le+1))

    figure(3); clf;
    h1 = freqz(h);
    h2 = freqz(flipud(w));
    h3 = freqz(conv(flipud(w),h));
    subplot(311); plot(20*log10(abs(h1)));
    title('channel, equaliser, combined freq resp')
    subplot(312); plot(20*log10(abs(h2)));
    subplot(313); plot(20*log10(abs(h3)));

    figure(4);
    subplot(211)
    plot(20*log10(abs(fft(s0M))))
    axis([1 length(s0M) 0 80]);
    grid;
    subplot(212)
    plot(20*log10(abs(fft(s))))
    axis([1 length(s0M) 0 80]);
    grid;

