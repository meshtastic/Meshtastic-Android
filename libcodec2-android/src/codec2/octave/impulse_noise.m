% impulse_noise
% David Rowe May 2017
%
% Experiments with impulsive noise and HF radio

format;
more off;
rand('seed',1)

% DFT function ------------------------------------------------
% note k is on 0..K-1 format, unlike Octave fft() which is 1..K

function H = calc_H(k, K, a, d)
  L = length(d);
  H = 0;
  for i=1:L
    H += a(i)*exp(-j*2*pi*k*d(i)/K);
  end
endfunction

% -----------------------------------------
% PWM noise simulation
% -----------------------------------------

function pwm_noise

  Fs   = 10E6;   % sample rate of simulation
  Fsig = 1E6;    % frequency of our wanted signal
  Fpwm = 255E3;  % switcher PWM frequency
  T    = 1;      % length of simulations in seconds
  Nsam = T*Fs;  
  Nsamplot = 200;
  Apwm = 0.1;
  Asig = -40;    % attenuation of wanted signal in dB

  % generate an impulse train with jitter to simulate switcher noise

  pwm = zeros(1,Fs);
  Tpwm = floor(Fs/Fpwm);
  pulse_positions_pwm = Tpwm*(1:T*Fpwm) + round(rand(1,T*Fpwm));

  h_pwm = zeros(1,Nsam);
  h_pwm(pulse_positions_pwm) = Apwm;
  h_pwm = h_pwm(1:Nsam);
 
  % add in wanted signal and computer amplitude spectrum

  s = 10^(Asig/20)*cos(2*pi*Fsig*(1:Nsam)/Fs);

  h = h_pwm+s;
  H = fft(h);
  Hdb = 20*log10(abs(H)) - 20*log10(Nsam/2);

  figure(1); clf;
  subplot(211)
  plot(h(1:Nsamplot));
  subplot(212)
  plot(Hdb(1:Nsam/2));
  axis([0 T*2E6 -120 0]); xlabel('Frequency Hz'); ylabel('Amplityude dBV'); grid;

  printf("pwm rms: %f signal rms: %f noise rms\n", std(h_pwm), std(s));
endfunction

% -----------------------------------------
% Single pulse noise simulation
% -----------------------------------------

function pulse_noise

  % set up short pulse in wide window, consisting of two samples next
  % to each other

  K = 1024;
  a(1) = a(2) = 1; d(1) = 10; d(2) = d(1)+1;
  h = zeros(1,K);
  h(d(1)) = a(1);
  h(d(2)) = a(2);

  % mag and phase spectrum, mag spectrum changes slowly

  figure(2); clf;
  Hfft = fft(h);
  subplot(311)
  stem(h(1:100));
  axis([1 100 -0.2 1.2]);
  subplot(312)  
  plot(abs(Hfft(1:K/2)),'+');
  title('Magnitude');
  subplot(313)
  plot(angle(Hfft(1:K/2)),'+');
  title('Phase');

  % simple test to estimate H(k+1) from H(k) --------------------

  % brute force calculation

  k = 300;
  H = zeros(1,K);
  H(k-1) = calc_H(k-1, K, a, d);
  H(k)   = calc_H(k, K, a, d);
  H(k+1) = calc_H(k+1, K, a, d);

  % calculation of k+1 from k using approximation that {d(i)} are
  % close together compared to M, i.e it's a narrow pulse (assumes we
  % can estimate d using other means)

  Hk1_ = exp(-j*2*pi*d(1)/K)*H(k);

  % plot zoomed in version around k to compare

  figure(3); clf;
  plot(H(k-1:k+1),'b+','markersize', 10, 'linewidth', 2);
  hold on; plot(Hk1_,'g+','markersize', 10, 'linewidth', 2); hold off;
  title('H(k-1) .... H(k+1)');
  printf("H(k+1) match: %f dB\n", 20*log10(abs(H(k+1) - Hk1_)));
endfunction

% Run various simulations here ---------------------------------------------

%pwm_noise
pulse_noise

