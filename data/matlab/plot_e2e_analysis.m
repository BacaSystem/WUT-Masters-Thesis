%% E2E Time Analysis - Thesis Quality Plot
% This script loads all model benchmark CSVs and creates comprehensive
% figures analyzing End-to-End (E2E) times for all 6 models

clear all; close all; clc;

% Define paths
data_path = 'C:/Studia/mgr/matlab/results/';
output_path = 'C:/Studia/mgr/matlab/results/figures/';

% Create output directory if it doesn't exist
if ~exist(output_path, 'dir')
    mkdir(output_path);
end

% Define model information (filename, display name, color, category)
models = struct();

models(1).file = 'openai4_20251215_220125.csv';
models(1).name = 'OpenAI GPT-4o';
models(1).color = hex2rgb('#2E75B6'); 
models(1).category = 'Cloud';

models(2).file = 'gemini_20251212_212010.csv';
models(2).name = 'Google Gemini';
models(2).color = hex2rgb('#4A90E2'); 
models(2).category = 'Cloud';

models(3).file = 'azure_20251221_124934.csv';
models(3).name = 'Azure CV';
models(3).color = hex2rgb('#70B4E6'); 
models(3).category = 'Cloud';

% Local models
models(4).file = 'vit-gpt2_20251212_215305.csv';
models(4).name = 'ViT-GPT2';
models(4).color = hex2rgb('#D97634');
models(4).category = 'Local';

models(5).file = 'blip_20251212_213002.csv';
models(5).name = 'BLIP';
models(5).color = hex2rgb('#E8913D'); 
models(5).category = 'Local';

models(6).file = 'florence_merged_all.csv';
models(6).name = 'Florence 2';
models(6).color = hex2rgb('#F5A962'); 
models(6).category = 'Local';

% Define colors for overlaid figure
overlaid_colors = struct();
overlaid_colors(1).color = hex2rgb('#1171BE');
overlaid_colors(2).color = hex2rgb('#2FBEEF');
overlaid_colors(3).color = hex2rgb('#3BAA32');
overlaid_colors(4).color = hex2rgb('#8516D1');
overlaid_colors(5).color = hex2rgb('#EDB120');
overlaid_colors(6).color = hex2rgb('#DD5400');

% Initialize storage for E2E times
num_models = length(models);
e2e_data = cell(num_models, 1);
inference_data = cell(num_models, 1);
e2e_mean = zeros(num_models, 1);
e2e_median = zeros(num_models, 1);
e2e_std = zeros(num_models, 1);
e2e_min = zeros(num_models, 1);
e2e_max = zeros(num_models, 1);
e2e_q25 = zeros(num_models, 1);
e2e_q75 = zeros(num_models, 1);
inference_median = zeros(num_models, 1);
model_names_cell = cell(num_models, 1);

% Load and process E2E times
for i = 1:num_models
    filepath = [data_path, models(i).file];
    
    % Check if file exists
    if ~isfile(filepath)
        warning(['File not found: ', filepath]);
        model_names_cell{i} = models(i).name;
        continue;
    end
    
    % Read CSV file
    try
        data = readtable(filepath);
        
        % Extract E2E times and inference times (only successful runs)
        if ismember('e2e_ms', data.Properties.VariableNames) && ismember('success', data.Properties.VariableNames)
            e2e_times = data.e2e_ms(data.success == 1);
            e2e_data{i} = e2e_times;
            
            % Extract inference times if available
            if ismember('inference_ms', data.Properties.VariableNames)
                inference_times = data.inference_ms(data.success == 1);
                inference_data{i} = inference_times;
                inference_median(i) = median(inference_times);
            else
                inference_median(i) = 0;
            end
            
            % Calculate statistics
            e2e_mean(i) = mean(e2e_times);
            e2e_median(i) = median(e2e_times);
            e2e_std(i) = std(e2e_times);
            e2e_min(i) = min(e2e_times);
            e2e_max(i) = max(e2e_times);
            e2e_q25(i) = prctile(e2e_times, 25);
            e2e_q75(i) = prctile(e2e_times, 75);
            
            model_names_cell{i} = models(i).name;
            
            fprintf('%s: Median E2E = %.1f ms\n', ...
                models(i).name, e2e_median(i));
        else
            error('Missing required columns');
        end
        
    catch ME
        warning(['Error processing ', models(i).file, ': ', ME.message]);
        model_names_cell{i} = models(i).name;
    end
end

%% Figure 2: Median E2E Time Comparison
figure('Name', 'Median E2E Time', 'Position', [100 100 900 600]);
set(gcf, 'Color', 'white');

ax = axes();
hold(ax, 'on');

% Create bar chart
x = 1:num_models;
width = 0.6;

b = bar(ax, x, e2e_median, width, 'FaceColor', 'flat', 'EdgeColor', 'none');
b.FaceColor = 'flat';

% Apply model-specific colors
for i = 1:num_models
    b.CData(i, :) = models(i).color;
end

% Styling
ax.FontSize = 11;
ax.FontName = 'Calibri';
set(ax, 'XTick', 1:num_models);
set(ax, 'XTickLabel', model_names_cell);
ax.YLabel.String = 'Median E2E Time [ms]';
ax.YLabel.FontSize = 12;
ax.YLabel.FontName = 'Calibri';
ax.YLabel.FontWeight = 'bold';
ax.XLabel.String = 'Model';
ax.XLabel.FontSize = 12;
ax.XLabel.FontName = 'Calibri';
ax.XLabel.FontWeight = 'bold';

ax.YGrid = 'on';
ax.GridLineStyle = '--';
ax.GridAlpha = 0.3;
ax.XGrid = 'off';

% Add value labels on top of bars
for i = 1:num_models
    if e2e_median(i) > 0
        text(i, e2e_median(i) + max(e2e_median)*0.02, sprintf('%.0f ms', e2e_median(i)), ...
            'HorizontalAlignment', 'center', ...
            'FontSize', 10, ...
            'FontName', 'Calibri', ...
            'FontWeight', 'bold');
    end
end

title('Median End-to-End Time for AI models', ...
    'FontSize', 14, 'FontName', 'Calibri', 'FontWeight', 'bold');

set(ax, 'Box', 'on', 'LineWidth', 1.2);
ax.TickDir = 'in';
ax.TickLength = [0.00 0.03];

set(gcf, 'PaperUnits', 'inches');
set(gcf, 'PaperSize', [9 6]);
set(gcf, 'PaperPosition', [0 0 9 6]);
pause(0.1);
set(gca, 'Position', [0.13 0.18 0.82 0.75]);

print(gcf, [output_path, 'e2e_median_comparison.png'], '-dpng', '-r300');
print(gcf, [output_path, 'e2e_median_comparison.pdf'], '-dpdf', '-r300');
fprintf('Median E2E comparison figure saved\n');

%% Figure 3-8: Individual E2E Time Over Runs (3x2 Subplot Grid)
figure('Name', 'E2E Over Runs - Individual Models', 'Position', [100 100 1200 800]);
set(gcf, 'Color', 'white');

posOrder = [1, 3, 5, 2, 4, 6];

for model_idx = 1:num_models
    if isempty(e2e_data{model_idx})
        continue;
    end
    
    subplot(3, 2, posOrder(model_idx));
    ax = gca();
    hold(ax, 'on');
    
    % Plot E2E time over runs (run number on X-axis)
    run_numbers = 1:length(e2e_data{model_idx});
    plot(ax, run_numbers, e2e_data{model_idx}, '-', ...
        'LineWidth', 1.2, 'DisplayName', 'E2E Time');
    
    % Add horizontal line for median
    med_val = e2e_median(model_idx);
    yline(ax, med_val, '--r', 'LineWidth', 1.5, 'DisplayName', sprintf('Median: %.0f ms', med_val));
    
    % Styling
    ax.FontSize = 9;
    ax.FontName = 'Calibri';
    ax.XLabel.String = 'Run Number';
    ax.XLabel.FontSize = 10;
    ax.YLabel.String = 'E2E Time [ms]';
    ax.YLabel.FontSize = 10;
    
    ax.YGrid = 'on';
    ax.GridLineStyle = '--';
    ax.GridAlpha = 0.3;
    ax.XGrid = 'off';
    
    % Zoom in on Y-axis for Azure
    if model_idx == 3
        ax.YLim = [0 1200];
    end
    
    title(sprintf('%s', model_names_cell{model_idx}), ...
        'FontSize', 11, 'FontName', 'Calibri', 'FontWeight', 'bold');
    
    legend('FontSize', 8, 'FontName', 'Calibri', 'Location', 'northwest', 'Box', 'off');
   
    set(ax, 'Box', 'on', 'LineWidth', 1);
    ax.TickDir = 'in';
    ax.TickLength = [0.00 0.00];
end

sgtitle('End-to-End Time', ...
    'FontSize', 14, 'FontName', 'Calibri', 'FontWeight', 'bold');

set(gcf, 'PaperUnits', 'inches');
set(gcf, 'PaperSize', [12 8]);
set(gcf, 'PaperPosition', [0 0 12 8]);
pause(0.1);

print(gcf, [output_path, 'e2e_over_runs_individual_subplots.png'], '-dpng', '-r300');
print(gcf, [output_path, 'e2e_over_runs_individual_subplots.pdf'], '-dpdf', '-r300');
fprintf('Individual E2E time plots (3x2 subplot) saved\n');

%% Figure 9: Combined E2E Time Over Runs (All Models Overlaid)
figure('Name', 'E2E Time - All Models', 'Position', [100 100 1000 700]);
set(gcf, 'Color', 'white');

ax = axes();
hold(ax, 'on');

% Plot all models with E2E time over runs
for i = 1:num_models
    if ~isempty(e2e_data{i})
        run_numbers = 1:length(e2e_data{i});
        plot(ax, run_numbers, e2e_data{i}, '-', 'Color', overlaid_colors(i).color, ...
            'LineWidth', 1.2, 'DisplayName', model_names_cell{i});
    end
end

% Styling
ax.FontSize = 11;
ax.FontName = 'Calibri';
ax.XLabel.String = 'Run Number';
ax.XLabel.FontSize = 12;
ax.XLabel.FontName = 'Calibri';
ax.XLabel.FontWeight = 'bold';
ax.YLabel.String = 'E2E Time [ms]';
ax.YLabel.FontSize = 12;
ax.YLabel.FontName = 'Calibri';
ax.YLabel.FontWeight = 'bold';

ax.YGrid = 'on';
ax.GridLineStyle = '--';
ax.GridAlpha = 0.3;
ax.XGrid = 'off';

title('End-to-End Time - All Models', ...
    'FontSize', 14, 'FontName', 'Calibri', 'FontWeight', 'bold');

legend('FontSize', 10, 'FontName', 'Calibri', 'Location', 'best', 'Box', 'off');

set(ax, 'Box', 'on', 'LineWidth', 1.2);
ax.TickDir = 'in';
ax.TickLength = [0.00 0.00];

set(gcf, 'PaperUnits', 'inches');
set(gcf, 'PaperSize', [10 7]);
set(gcf, 'PaperPosition', [0 0 10 7]);
pause(0.1);

print(gcf, [output_path, 'e2e_over_runs_all_models_overlaid.png'], '-dpng', '-r300');
print(gcf, [output_path, 'e2e_over_runs_all_models_overlaid.pdf'], '-dpdf', '-r300');
fprintf('Combined E2E time plot (all models overlaid) saved\n');

fprintf('\nFigures saved to: %s\n', output_path);
fprintf('Figures: e2e_distribution_boxplot, e2e_median_comparison\n');
fprintf('Individual plots: e2e_over_runs_individual_subplots (3x2 grid)\n');
fprintf('Combined plot: e2e_over_runs_all_models_overlaid\n');
