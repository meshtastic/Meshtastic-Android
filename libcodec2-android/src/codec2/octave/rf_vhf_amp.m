% s_param_rf.m
%
% David Rowe Nov 2015
%
% Working for small signal VHF amplifier design using
% S-param techniques from "RF Circuit Design" by Chris Bowick

rfdesign; % library of helped functions

more off;

Ic = 0.014;

% BRF92 VCE=5V Ic=5mA 100MHz
 
if Ic == 0.005
  S11 = 0.727*exp(j*(-43)*pi/180);
  S12 = 0.028*exp(j*(69.6)*pi/180);
  S21 = 12.49*exp(j*(147)*pi/180);
  S22 = 0.891*exp(j*(-16)*pi/180);
end

% BRF92 VCE=10V Ic=14mA 100MHz

if Ic == 0.02
  S11 = 0.548*exp(j*(-56.8)*pi/180);
  S12 = 0.020*exp(j*(67.8)*pi/180);
  S21 = 20.43*exp(j*(133.7)*pi/180);
  S22 = 0.796*exp(j*(-18.5)*pi/180);
end

% Stability

Ds = S11*S22-S12*S21;
Knum = 1 + abs(Ds)^2 - abs(S11)^2 - abs(S22)^2;
Kden = 2*abs(S21)*abs(S12);
K = Knum/Kden                                    % If > 1 unconditionally stable
                                                 % If < 1 panic
figure(1);
clf
scCreate;

if K < 1
  C1 = S11 - Ds*conj(S22);
  C2 = S22 - Ds*conj(S11);
  rs1 = conj(C1)/(abs(S11)^2-abs(Ds)^2);           % centre of input stability circle
  ps1 = abs(S12*S21/(abs(S11)^2-abs(Ds)^2));       % radius of input stability circle
  rs2 = conj(C2)/(abs(S22)^2-abs(Ds)^2);           % centre of input stability circle
  ps2 = abs(S12*S21/(abs(S22)^2-abs(Ds)^2));       % radius of input stability circle

  s(1,1)=S11; s(1,2)=S12; s(2,1)=S21; s(2,2)=S22;
  plotStabilityCircles(s)
end

% Gain circle

D2 = abs(S22)^2-abs(Ds)^2;
C2 = S22 - Ds*conj(S11);
GdB = 20; Glin = 10^(GdB/10);                                         % lets shoot for 20dB gain
G = Glin/(abs(S21)^2);
r0 = G*conj(C2)/(1+D2*G);                                             % centre of gain circle
p0 = sqrt(1 - 2*K*abs(S12*S21)*G + (abs(S12*S21)^2)*(G^2))/(1+D2*G);  % radius of gain circle

scAddCircle(abs(r0),angle(r0)*180/pi,p0,'g')
printf("Green is the %3.1f dB constant gain circle for gammaL\n",GdB);

% Note different design procedures for different operating points

if Ic == 0.005
  % Choose a gammaL on the gain circle

  gammaL = 0.8 - 0.4*j;

  % Caclulate gammaS and make sure it's stable by visual inspection
  % compared to stability circle.

  gammaS = conj(S11 + ((S12*S21*gammaL)/(1 - (gammaL*S22))));
end

if Ic == 0.014

  % lets set zo (normalised Zo) based on Pout and get gammaL from that

  Pout = 0.01;
  Irms = 0.002;
  Zo = Pout/(Irms*Irms);
  zo = Zo/50;
  [magL,angleL] = ztog(zo);
  gammaL = magL*exp(j*angleL*pi/180);

  % calculate gammaS

  gammaS = conj(S11 + ((S12*S21*gammaL)/(1 - (gammaL*S22))));

end

[zo Zo] = gtoz(abs(gammaL), angle(gammaL)*180/pi,50);
[zi Zi] = gtoz(abs(gammaS), angle(gammaS)*180/pi,50);

scAddPoint(zi);
scAddPoint(zo);

% Transducer gain

Gt_num = (abs(S21)^2)*(1-abs(gammaS)^2)*(1-abs(gammaL)^2);
Gt_den = abs((1-S11*gammaS)*(1-S22*gammaL) - S12*S21*gammaL*gammaS)^2;
Gt = Gt_num/Gt_den;

if Ic == 0.005

  % Lets design the z match for the input ------------------------------

  % put input impedance in parallel form

  Zip = zs_to_zp(Zi);

  % first match real part of impedance

  Rs = 50; Rl = real(Zip);
  [Xs Xp] = z_match(Rs,Rl);
  
  % Modify Xp so transistor input sees conjugate match to Zi
  % Lets make Xp a capacitor, so negative sign

  Xp_match = -Xp - imag(Zip);

  % Now convert to real component values

  w = 2*pi*150E6;
  Ls = Xs/w; diameter_mm = 6.25; 
  Ls_turns = design_inductor(Ls*1E6, diameter_mm);
  Cp = 1/(w*(-Xp_match));

  printf("Transducer gain: %3.1f dB\n", 10*log10(Gt));
  printf("Input: Zi = %3.1f + %3.1fj ohms\n", real(Zi), imag(Zi));
  printf("       In parallel form Rp = %3.1f Xp = %3.1fj ohms\n", real(Zip), imag(Zip));
  printf("       So for a conjugate match transistor input wants to see:\n         Rp = %3.1f Xp = %3.1fj ohms\n", real(Zip), -imag(Zip));
  printf("       Rs = %3.1f to Rl = %3.1f ohm matching network Xs = %3.1fj Xp = %3.1fj\n", Rs, Rl, Xs, Xp);
  printf("       with conj match to Zi Xs = %3.1fj Xp = %3.1fj\n", Xs, Xp_match);
  printf("       matching components Ls = %5.3f uH Cp = %4.1f pF\n", Ls*1E6, Cp*1E12);
  printf("       Ls can be made from %3.1f turns on a %4.2f mm diameter air core\n", Ls_turns, diameter_mm);

  % Now Z match for output -------------------------------------

  Lo = -imag(Zo)/w;
  Lo_turns = design_inductor(Lo*1E6, diameter_mm);
  printf("Output: Zo = %3.1f + %3.1fj ohms\n", real(Zo), imag(Zo));
  printf("        So for a conjugate match transistor output wants to see:\n          Rl = %3.1f Xl = %3.1fj ohms\n", real(Zo), -imag(Zo));
  printf("        Which is a series inductor Lo = %5.3f uH\n", Lo*1E6);
  printf("        Lo can be made from %3.1f turns on a %4.2f mm diameter air core\n", Lo_turns, diameter_mm);
end


if Ic == 0.014
  printf("Transducer gain: %3.1f dB\n", 10*log10(Gt));

  % Lets design the z match for the input ------------------------------

  % put input impedance in parallel form

  Zip = zs_to_zp(Zi);

  % first match real part of impedance

  Rs = 50; Rl = real(Zip);
  [Xs Xp] = z_match(Rl,Rs);
  
  % Lets make Xs a capacitir to block DC, so Xp is an inductor.
  % Modify Xs so transistor input sees conjugate match to Zi. Xs is a
  % capacitor, so reactance is negative

  Xs_match = -Xs - imag(Zip);

  % Now convert to real component values

  w = 2*pi*150E6; diameter_mm = 6.25; 
  Li = Xp/w;
  Li_turns = design_inductor(Li*1E6, diameter_mm);
  Ci = 1/(w*(-Xs_match));

  printf("Input: Zi = %3.1f + %3.1fj ohms\n", real(Zi), imag(Zi));
  printf("       In parallel form Rp = %3.1f Xp = %3.1fj ohms\n", real(Zip), imag(Zip));
  printf("       So for a conjugate match transistor input wants to see:\n         Rp = %3.1f Xp = %3.1fj ohms\n", real(Zip), -imag(Zip));
  printf("       Rs = %3.1f to Rl = %3.1f ohm matching network Xs = %3.1fj Xp = %3.1fj\n", Rs, Rl, Xs, Xp);
  printf("         with Xs a capacitor, and Xp and inductor Xs = %3.1fj Xp = %3.1fj\n", -Xs, Xp);
  printf("       With a conj match to Zi Xs = %3.1fj Xp = %3.1fj\n", Xs_match, Xp);
  printf("       matching components Li = %5.3f uH Ci = %4.1f pF\n", Li*1E6, Ci*1E12);
  printf("       Li can be made from %3.1f turns on a %4.2f mm diameter air core\n", Li_turns, diameter_mm);

  % Design output Z match ----------------------------------------------

  Rs = real(Zo);  Rl = 50;
  [Xs Xp] = z_match(Rl,Rs);

  % Lets make XP an inductor so it can double as a RF choke, and Xp as
  % a capacitor will give us a convenient DC block

  w = 2*pi*150E6; diameter_mm = 6.25;
  Lo = Xp/w; Lo_turns = design_inductor(Lo*1E6, diameter_mm);
  Co = 1/(w*Xs);
  printf("Output: Zo = %3.1f + %3.1fj ohms\n", real(Zo), imag(Zo));
  printf("        matching network Xp = %3.1f X = %3.1f ohms\n", Xp, Xs);
  printf("        which is parallel Lo = %5.3f uH and series Co = %4.1f pF\n", Lo*1E6, Co*1E12);
  printf("        Lo can be made from %3.1f turns on a %4.2f mm diameter air core\n", Lo_turns, diameter_mm);
end

