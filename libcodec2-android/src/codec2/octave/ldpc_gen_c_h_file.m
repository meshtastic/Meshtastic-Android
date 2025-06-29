% ldpc_gen_c_h_file.m
% David Rowe Sep 2015, B. Van Slyke 2019
%
% Create .c and h files for use in LDPC decoders
%
% NOTE:  You'll need to install the CML library as a number of functions involved
%        in LDPC use it.  See ldpc.m for instructions in installing the CML
%        library.
%
% usage examples:
%
%   1/ Using codes defined in external files:
%
%     octave:1> ldpc_gen_c_h_file("HRA_112_112.txt")
%     octave:1> ldpc_gen_c_h_file(""H_4096_8192_3d.mat")
%
%   2/ Using built in CML codes:
%
%     octave:1> ldpc_gen_c_h_file("dvbs2", 0.6, 16200)
%
% Output: Two files with the same filename as the LDPC input, but with .c and .h
% extensions.

function ldpc_gen_c_h_file(varargin)

    ldpc  % load ldpc functions
    ldpc_fsk_lib  % for ldpc_encode

    % Assuming cml has been installed in the users' home folder, which is the
    % default install location
    init_cml();

    if nargin == 0
        printf("Error - you must specify a file containing the LDPC codes (e.g. HRA_112_112.txt).\n");
        return;
    end
    loadStr = varargin{1};

    max_iterations = 100;
    decoder_type = 0;
    % the tests are performed using BPSK modulation, but in practice codes can be used
    % with other modulation, e.g. QPSK
    mod_order = 2; modulation = 'BPSK'; mapping = 'gray';

    if strcmp(loadStr, "dvbs2")
        rate = varargin{2};
        framesize = varargin{3};
        code_param = ldpc_init_builtin(loadStr, rate, framesize, modulation, mod_order, mapping);
        n = code_param.ldpc_coded_bits_per_frame;
        k = code_param.ldpc_data_bits_per_frame;
        ldpcArrayName = sprintf("H_%d_%d",n,k);
        includeFileName = strcat(ldpcArrayName, '.h');
        sourceFileName = strcat(ldpcArrayName,  '.c');
    else
        % The ldpc variable name may not be what we want for a file/variable names, but
        % the load filename will be, so use it.
        [~,ldpcArrayName,ext] = fileparts(loadStr);
        includeFileName = strcat(ldpcArrayName, '.h');
        sourceFileName = strcat(ldpcArrayName,  '.c');

        % Get the ext of the file first.  If it's a txt, then do what we
        % are doing.  If .mat, then just load, knowing the variable is HRA
        if strcmp(ext, '.mat') == 1
            load(loadStr);
            if exist("H") & !exist("HRA")
                printf("renaming H to HRA...\n");
                HRA=H;
            end
        else
            % When calling 'load' this way, it returns a struct.  The code assumes the
            % struct has one element, and the one/first element is the array
            % to process
            tempStruct = load(loadStr);
            b = fieldnames(tempStruct);
            ldpcArrayName = b{1,1};
            % extract the array from the struct
            HRA = tempStruct.(ldpcArrayName);
        endif

        code_param = ldpc_init_user(HRA, modulation, mod_order, mapping);
    end

    code_length = code_param.coded_syms_per_frame;

    % First, create the H file
    f = fopen(includeFileName, "wt");
    printHeader(f, includeFileName, ldpcArrayName, mfilename());

    fprintf(f,"#define %s_NUMBERPARITYBITS %d\n", ldpcArrayName, rows(code_param.H_rows));
    fprintf(f,"#define %s_MAX_ROW_WEIGHT %d\n", ldpcArrayName, columns(code_param.H_rows));
    fprintf(f,"#define %s_CODELENGTH %d\n", ldpcArrayName, code_param.coded_syms_per_frame);
    fprintf(f,"#define %s_NUMBERROWSHCOLS %d\n", ldpcArrayName, rows(code_param.H_cols));
    fprintf(f,"#define %s_MAX_COL_WEIGHT %d\n", ldpcArrayName, columns(code_param.H_cols));
    fprintf(f,"#define %s_DEC_TYPE %d\n", ldpcArrayName, decoder_type);
    fprintf(f,"#define %s_MAX_ITER %d\n", ldpcArrayName, max_iterations);
    fprintf(f,"\n");
    fprintf(f,"extern const uint16_t %s_H_rows[];\n", ldpcArrayName);
    fprintf(f,"extern const uint16_t %s_H_cols[];\n", ldpcArrayName);

    fclose(f);


    % Then, the C file
    f = fopen(sourceFileName, "wt");
    printHeader(f, sourceFileName, ldpcArrayName, mfilename());
    fprintf(f, "#include <stdint.h>\n");
    fprintf(f, "#include \"%s\"\n", includeFileName);

    % clock out 2D array to linear C array in row order ....
    fprintf(f,"\nconst uint16_t %s_H_rows[] = {\n", ldpcArrayName);
    [r c] = size(code_param.H_rows);
    for j=1:c
        for i=1:r
            fprintf(f, "%d", code_param.H_rows(i,j));
            if (i == r) && (j ==c)  % weird, this does nothing
                fprintf(f,"\n};\n");
            else
                fprintf(f,", ");
            end
        end
    end

    fprintf(f,"\nconst uint16_t %s_H_cols[] = {\n", ldpcArrayName);
    [r c] = size(code_param.H_cols);
    for j=1:c
        for i=1:r
            fprintf(f, "%d", code_param.H_cols(i,j));
            if (i == r) && (j == c)
                fprintf(f,"\n};\n");
            else
                fprintf(f,", ");
            end
        end
    end

    fclose(f);
endfunction

function printHeader(f, includeFileName, ldpcArrayName, mFilename)
    fprintf(f, "/*\n  FILE....: %s\n\n", includeFileName);
    fprintf(f, "  Static arrays for LDPC codec %s, generated by %s.m.\n*/\n\n", ldpcArrayName, mFilename);
endfunction
