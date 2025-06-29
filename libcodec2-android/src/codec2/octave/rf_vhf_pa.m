% vhf_pa.m
%
% David Rowe Dec 2015
%
% Working for 0.5W VHF PA

rfdesign;

% BFQ19 Vce=5V Ic=50mA.  These are small signal S-params,
% which (according to "RF Cicruit Design") are not valid.
% However I need to start somewhere.

S11 = 0.324*exp(j*(-158.1)*pi/180);
S12 = 0.031*exp(j*(75.9)*pi/180);
S21 = 19.693*exp(j*(102.7)*pi/180);
S22 = 0.274*exp(j*(-74.6)*pi/180);

% Lets check stability

Ds = S11*S22-S12*S21;
Knum = 1 + abs(Ds)^2 - abs(S11)^2 - abs(S22)^2;
Kden = 2*abs(S21)*abs(S12);
K = Knum/Kden
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


% determine collector load Rl for our desired power output

if 0
P = 0.5;
Vcc = 5;
w = 2*pi*150E6;

Rl = Vcc*Vcc/(2*P);
end
Rl = 10;

% choose gammaL based on Rl

zo = Rl/50;
[magL,angleL] = ztog(zo);
gammaL = magL*exp(j*angleL*pi/180);

% calculate gammaS and Zi and plot

gammaS = conj(S11 + ((S12*S21*gammaL)/(1 - (gammaL*S22))));
[zi Zi] = gtoz(abs(gammaS), angle(gammaS)*180/pi,50);

scAddPoint(zi);
scAddPoint(zo);

% design Pi network for matching Rl to Ro, where Ro > Rl
%
% /---+-Xs1-Xs2-+---\
% |   |         |   |
% Rl Xp1       Xp2  Ro
% |   |         |   |
% \---+---------+---/
%
% highest impedance used to define Q of pi network and determine R,
% the "virtual" impedance at the centre of the network, whuch is smaller
% than Rl and Ro

Ro = 50;
Q = 3;
R = Ro/(Q*Q+1); 

Xp2 = Ro/Q;
Xs2 = Q*R;

Q1 = sqrt(Rl/R - 1);
Xp1 = Rl/Q1;
Xs1 = Q1*R;

Cp1 = 1/(w*Xp1);
Cp2 = 1/(w*Xp2);
Ls  = (Xs1+Xs2)/w;

printf("Output Matching:\n");
printf("  Rl = %3.1f  Ro = %3.1f\n", Rl, Ro);
printf("  Q = %3.1f virtual R = %3.1f\n", Q, R);
printf("  Xp1 = %3.1f Xs1 = %3.1f Xs2 = %3.1f Xp2 = %3.1f\n", Xp1, Xs1, Xs2, Xp2);
printf("  Cp1 = %3.1f pF Ls = %3.1f nH Cp2 = %3.1f pF\n", Cp1*1E12, Ls*1E9, Cp2*1E12);

% design input matching network between 50 ohms source and 10 ohms at base

Rb = 10; Rs = 50;

[Xs Xp] = z_match(Rb, Rs);

Lp = Xp/w;
Cs = 1/(w*Xs);

printf("Input Matching:\n");
printf("  Xs = %3.1f Xp = %3.1f\n", Xs, Xp);
printf("  Lp = %3.1f nH Cs = %3.1f pF\n", Lp*1E9, Cs*1E12);

