% rfdesign.m
%
% David Rowe Nov 2015
%
% Helper functions for RF Design

1;


% convert a parallel R/X to a series R/X

function Zs = zp_to_zs(Zp)
  Xp = j*imag(Zp); Rp = real(Zp);
  Zs = Xp*Rp/(Xp+Rp);
endfunction


% convert a series R/X to a parallel R/X

function Zp = zs_to_zp(Zs)
  Xs = imag(Zs); Rs = real(Zs);
  Q = Xs/Rs;       
  Rp = (Q*Q+1)*Rs;
  Xp = Rp/Q;
  Zp = Rp + j*Xp;
endfunction


% Design a Z match network with a parallel and series reactance
% to match between a low and high resistance.  Note Xp and Xs
% must be implemented as opposite sign, ie one a inductor, one
% a capacitor (your choice).
%
%  /--Xs--+---\
%  |      |   |
% Rlow   Xp  Rhigh
%  |      |   |
%  \------+---/
%        

function [Xs Xp] = z_match(Rlow, Rhigh)
  assert(Rlow < Rhigh, "Rlow must be < Rhigh");
  Q = sqrt(Rhigh/Rlow -1);
  Xs = Q*Rlow;
  Xp = Rhigh/Q;
endfunction


% Design an air core inductor, Example 1-5 "RF Circuit Design"

function Nturns = design_inductor(L_uH, diameter_mm)
  Nturns = sqrt(29*L_uH/(0.394*(diameter_mm*0.1/2)));
endfunction


% Work out series resistance Rl of series resonant inductor.  Connect
% tracking generator to spec-an input, the series LC to ground.  V is
% the ref TG level (e.g. with perfect 50 ohm term) in volts, Vmin is the
% minumum at series res freq.
%
%  /-50-+---+
%  |    |   |
%  TG   C   50 spec-an
%  |    |   |
%  |    L   |
%  |    |   |
%  |    Rl  |
%  |    |   |
%  \----+---/

function Rl = find_rl(V,Vmin)
  % at series resonance effect of C and L goes away and we are left with
  % parallel combination of Ls and spec-an 50 ohm input impedance

  Rp = Vmin*50/(2*V*(1-Vmin/(2*V)));
  Rl = 1/(1/Rp - 1/50)
endfunction
