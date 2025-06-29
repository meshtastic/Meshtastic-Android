% Copyright David Rowe 2009
% This program is distributed under the terms of the GNU General Public License
% Version 2

function pl2(samname1, samname2, start_sam, end_sam, offset)

  fs1=fopen(samname1,"rb");
  s1=fread(fs1,Inf,"short");
  fs2=fopen(samname2,"rb");
  s2=fread(fs2,Inf,"short");

  st1 = st2 = 1;
  en1 = en2 = length(s1);
  if (nargin >= 3)
    st1 = st2 = start_sam;
  endif
  if (nargin >= 4)
    en1 = en2 = end_sam;
  endif

  if (nargin == 5)
    st2 += offset
    en2 += offset
  endif

  figure(1);
  clf;
  subplot(211);
  l1 = strcat("r;",samname1,";");
  plot(s1(st1:en1), l1); grid minor;
  axis([1 en1-st1 min(s1(st1:en1)) max(s1(st1:en1))]);
  subplot(212);
  l2 = strcat("r;",samname2,";");
  plot(s2(st2:en2),l2); grid minor;
  axis([1 en2-st2 min(s1(st2:en2)) max(s1(st2:en2))]);

  figure(2)
  plot(s1(st1:en1)-s2(st2:en2)); grid minor;

  f=fopen("diff.raw","wb");
  d = s1(st1:en1)-s2(st2:en2);
  fwrite(f,d,"short");

endfunction
