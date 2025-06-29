% pl_scatter.m
% Render scatter plot from freedv_data_raw_rx --scatter

function pl_scatter(filename)
    s=load(filename);
    figure(1); clf;
    for b=1:length(fieldnames(s))
        field_name = fieldnames(s){b};
        x = s.(field_name);
        plot(x,'+');
    end
    print("scatter.png", "-dpng");
endfunction
