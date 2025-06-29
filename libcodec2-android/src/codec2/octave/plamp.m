% Copyright David Rowe 2009
% This program is distributed under the terms of the GNU General Public License
% Version 2
%
% Plot ampltiude modelling information from dump files.

function plamp(samname, f, samname2)

  % switch some stuff off to unclutter display

  plot_sw = 0;

  sn_name = strcat(samname,"_sn.txt");
  Sn = load(sn_name);

  sw_name = strcat(samname,"_sw.txt");
  Sw = load(sw_name);

  sw__name = strcat(samname,"_sw_.txt");
  if (file_in_path(".",sw__name))
    Sw_ = load(sw__name);
  endif

  ew_name = strcat(samname,"_ew.txt");
  if (file_in_path(".",ew_name))
    Ew = load(ew_name);
  endif

  rk_name = strcat(samname,"_rk.txt");
  if (file_in_path(".",rk_name))
    Rk = load(rk_name);
  endif

  model_name = strcat(samname,"_model.txt");
  model = load(model_name);

  modelq_name = strcat(samname,"_qmodel.txt");
  if (file_in_path(".",modelq_name))
    modelq = load(modelq_name);
  endif

  pw_name = strcat(samname,"_pw.txt");
  if (file_in_path(".",pw_name))
    Pw = load(pw_name);
  endif

  lsp_name = strcat(samname,"_lsp.txt");
  if (file_in_path(".",lsp_name))
    lsp = load(lsp_name);
  endif

  phase_name = strcat(samname,"_phase.txt");
  if (file_in_path(".",phase_name))
    phase = load(phase_name);
  endif

  phase_name_ = strcat(samname,"_phase_.txt");
  if (file_in_path(".",phase_name_))
    phase_ = load(phase_name_);
  endif

  snr_name = strcat(samname,"_snr.txt");
  if (file_in_path(".",snr_name))
    snr = load(snr_name);
  endif

  % optional second file, for exploring post filter

  if nargin == 3
    model2_name = strcat(samname2,"_model.txt");
    model2 = load(model2_name);
    sn2_name = strcat(samname2,"_sn.txt");
    Sn2 = load(sn2_name);

    sw_name2 = strcat(samname2,"_sw.txt");
    Sw2 = load(sw_name2);
  end

  k = ' ';
  do
    figure(1);
    clf;
    s = [ Sn(2*f-1,:) Sn(2*f,:) ];
    plot(s,'b');
    if (nargin == 3)
      s2 = [ Sn2(2*f-1,:) Sn2(2*f,:) ];
      hold on; plot(s2,'r'); hold off;
    end
    axis([1 length(s) -30000 30000]);

    figure(2);
    Wo = model(f,1);
    L = model(f,2);
    Am = model(f,3:(L+2));
    plot((1:L)*Wo*4000/pi, 20*log10(Am),";Am;+-b");
    axis([1 4000 -10 80]);
    hold on;
    if plot_sw; plot((0:255)*4000/256, Sw(f,:),";Sw;b"); end

    if (nargin == 3)
      Wo2 = model2(f,1);
      L2 = model2(f,2);
      Am2 = model2(f,3:(L2+2));
      plot((1:L2)*Wo2*4000/pi, 20*log10(Am2),";Am2;+-r" );
      if plot_sw; plot((0:255)*4000/256, Sw2(f,:),";Sw2;r"); end
    endif

    hold off; grid minor;

    % interactive menu

    printf("\rframe: %d  menu: n-next  b-back  p-png  s-plot_sw q-quit", f);
    fflush(stdout);
    k = kbhit();
    if k == 'n'; f = f + 1; endif
    if k == 'b'; f = f - 1; endif
    if k == 's'
        if plot_sw; plot_sw = 0; else; plot_sw = 1; end
    endif
    % optional print to PNG

    if (k == 'p')
      figure(1);
      pngname = sprintf("%s_%d_sn.png",samname,f);
      print(pngname, '-dpng', "-S800,600")

      figure(2);
      pngname = sprintf("%s_%d_sw.png",samname,f);
      print(pngname, '-dpng', "-S800,600")
     endif

  until (k == 'q')
  printf("\n");

endfunction
