% tdetphase.m
% David Rowe August 2017
%
% Testing Hilbert Transform recover of phase from magnitude spectra

newamp;
Fs = 8000;

w = 2*pi*500/Fs; gamma = 0.95
ak = [1 -2*gamma*cos(w) gamma*gamma];
Nfft = 512;

% Test 1 - compare phase from freqz for 2nd order system (all pole filter)
%        - uses internal test of determine_phase()

h = freqz(1,ak,Nfft/2);

% note dummy_model not used, as determine_phase() is used in test mode

L = 20; Wo = pi/(L+1);
dummy_model = [Wo L ones(1,L)];
phase = determine_phase(dummy_model, 1, Nfft, ak);

fg = 1;
figure(fg++); clf;
subplot(211); plot(20*log10(abs(h))); title('test 1');
subplot(212); plot(angle(h)); hold on; plot(phase(1:Nfft/2),'g'); hold off;

% Test 2 - feed in harmonic magnitudes

F0 = 100; Wo = 2*pi*F0/Fs; L = floor(pi/Wo);
Am = zeros(1,L);
for m=1:L
  b = round(m*Wo*Nfft/(2*pi));
  Am(m) = abs(h(b));
end
AmdB = 20*log10(Am);
model = [Wo L Am];
[phase Gdbfk s] = determine_phase(model, 1, Nfft);

fftx = (1:Nfft/2)*(Fs/Nfft);
harmx = (1:L)*Wo*Fs/(2*pi);

figure(fg++); clf;
subplot(211); plot(fftx, Gdbfk(1:Nfft/2));
subplot(212); plot(s(1:Nfft/2))

figure(fg++); clf;
subplot(211); plot(fftx, 20*log10(abs(h)));
              hold on; plot(harmx, AmdB, 'g+'); plot(fftx, Gdbfk(1:Nfft/2), 'r'); hold off;
subplot(212); plot(fftx, angle(h)); hold on; plot(fftx, phase(1:Nfft/2),'g'); hold off;

% Test 3 - Use real harmonic amplitudes

model = load("../build_linux/src/hts1a_model.txt");
phase_orig = load("../build_linux/src/hts1a_phase.txt");

f = 184;
Wo = model(f,1); L = model(f,2); Am = model(f,3:L+2); AmdB = 20*log10(Am);
[phase Gdbfk s] = determine_phase(model, f, Nfft);

fftx = (1:Nfft/2)*(Fs/Nfft);
harmx = (1:L)*Wo*Fs/(2*pi);

figure(fg++); clf;
subplot(211); plot(fftx, Gdbfk(1:Nfft/2));
subplot(212); plot(s(1:Nfft/2))

figure(fg++); clf;
subplot(211); plot(harmx, AmdB, 'g+');
              hold on; plot(fftx, Gdbfk(1:Nfft/2), 'r'); hold off;
subplot(212); plot(fftx, phase(1:Nfft/2),'g');

% synthesise using phases

N = 320;
s = s_phase = zeros(1,N);
for m=1:L/4
  s = s + Am(m)*cos(m*Wo*(1:N) + phase_orig(f,m));
  b = round(m*Wo*Nfft/(2*pi));
  s_phase = s_phase + Am(m)*cos(m*Wo*(1:N) + phase(b));
end
figure(fg++); clf;
subplot(211); plot(s); subplot(212); plot(s_phase,'g');
