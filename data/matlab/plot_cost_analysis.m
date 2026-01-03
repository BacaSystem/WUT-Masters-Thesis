%% Cost Analysis - Thesis Quality Plot
% This script loads all model benchmark CSVs and creates a professional
% figure analyzing Cost (USD) for all 6 models

clear all; close all; clc;

% Define paths
data_path = 'C:/Studia/mgr/data/matlab/results/';
output_path = 'C:/Studia/mgr/data/matlab/results/figures/';

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

% Initialize storage for cost and token data
num_models = length(models);
cost_data = cell(num_models, 1);
token_data = cell(num_models, 1);
cost_median = zeros(num_models, 1);
cost_mean = zeros(num_models, 1);
cost_std = zeros(num_models, 1);
cost_min = zeros(num_models, 1);
cost_max = zeros(num_models, 1);
token_median = zeros(num_models, 1);
token_mean = zeros(num_models, 1);
token_std = zeros(num_models, 1);
token_min = zeros(num_models, 1);
token_max = zeros(num_models, 1);
model_names_cell = cell(num_models, 1);

% Load and process cost data
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
        
        % Extract cost (only successful runs with non-empty cost values)
        if ismember('cost_usd', data.Properties.VariableNames) && ismember('success', data.Properties.VariableNames)
            % Filter successful runs and non-empty cost values
            valid_idx = (data.success == 1) & (data.cost_usd > 0);
            cost_values = data.cost_usd(valid_idx);
            cost_data{i} = cost_values;
            
            if ~isempty(cost_values)
                % Calculate statistics
                cost_mean(i) = mean(cost_values);
                cost_median(i) = median(cost_values);
                cost_std(i) = std(cost_values);
                cost_min(i) = min(cost_values);
                cost_max(i) = max(cost_values);
                
                model_names_cell{i} = models(i).name;
                
                fprintf('%s: Median Cost = $%.6f, Mean = $%.6f\n', ...
                    models(i).name, cost_median(i), cost_mean(i));
                
                % Extract tokens if available (same valid indices as cost)
                if ismember('total_tokens', data.Properties.VariableNames)
                    token_values = data.total_tokens(valid_idx);
                    % Filter out zero/empty token values
                    token_valid = token_values(token_values > 0);
                    token_data{i} = token_valid;
                    
                    if ~isempty(token_valid)
                        token_mean(i) = mean(token_valid);
                        token_median(i) = median(token_valid);
                        token_std(i) = std(token_valid);
                        token_min(i) = min(token_valid);
                        token_max(i) = max(token_valid);
                        
                        fprintf('  Tokens - Median = %.0f, Mean = %.0f (n=%d)\n', ...
                            token_median(i), token_mean(i), length(token_valid));
                    end
                end
            else
                warning(['No valid cost data for ', models(i).name]);
                model_names_cell{i} = models(i).name;
            end
        else
            warning(['Missing cost_usd column in ', models(i).file]);
            model_names_cell{i} = models(i).name;
        end
        
    catch ME
        warning(['Error processing ', models(i).file, ': ', ME.message]);
        model_names_cell{i} = models(i).name;
    end
end

%% Figure: Median Cost Comparison
figure('Name', 'Median Cost', 'Position', [100 100 900 600]);
set(gcf, 'Color', 'white');

ax = axes();
hold(ax, 'on');

% Create bar colors
bar_colors = zeros(num_models, 3);
for i = 1:num_models
    bar_colors(i, :) = models(i).color;
end

% Create bars
b = bar(ax, cost_median, 'BarWidth', 0.6);
b.FaceColor = 'flat';
for i = 1:num_models
    b.CData(i, :) = bar_colors(i, :);
end

% Styling
ax.FontSize = 11;
ax.FontName = 'Calibri';
set(ax, 'XTick', 1:num_models);
set(ax, 'XTickLabel', model_names_cell);
ax.YLabel.String = 'Cost (USD)';
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
    if cost_median(i) > 0
        text(i, cost_median(i) + max(cost_median)*0.02, sprintf('$%.6f', cost_median(i)), ...
            'HorizontalAlignment', 'center', ...
            'FontSize', 10, ...
            'FontName', 'Calibri', ...
            'FontWeight', 'bold');
    end
end

title('Median Cost per Inference', ...
    'FontSize', 14, 'FontName', 'Calibri', 'FontWeight', 'bold');

set(ax, 'Box', 'on', 'LineWidth', 1.2);
ax.TickDir = 'out';
ax.TickLength = [0.00 0.00];

set(gcf, 'PaperUnits', 'inches');
set(gcf, 'PaperSize', [9 6]);
set(gcf, 'PaperPosition', [0 0 9 6]);
pause(0.1);
set(gca, 'Position', [0.13 0.18 0.82 0.75]);

print(gcf, [output_path, 'cost_median_comparison.png'], '-dpng', '-r300');
print(gcf, [output_path, 'cost_median_comparison.pdf'], '-dpdf', '-r300');
fprintf('Median cost figure saved\n');

%% Figure: Median Token Usage Comparison
figure('Name', 'Median Tokens', 'Position', [100 100 900 600]);
set(gcf, 'Color', 'white');

ax = axes();
hold(ax, 'on');

% Create bar colors
bar_colors = zeros(num_models, 3);
for i = 1:num_models
    bar_colors(i, :) = models(i).color;
end

% Create bars
b = bar(ax, token_median, 'BarWidth', 0.6);
b.FaceColor = 'flat';
for i = 1:num_models
    b.CData(i, :) = bar_colors(i, :);
end

% Styling
ax.FontSize = 11;
ax.FontName = 'Calibri';
set(ax, 'XTick', 1:num_models);
set(ax, 'XTickLabel', model_names_cell);
ax.YLabel.String = 'Median Tokens Used';
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
    if token_median(i) > 0
        text(i, token_median(i) + max(token_median)*0.02, sprintf('%.0f', token_median(i)), ...
            'HorizontalAlignment', 'center', ...
            'FontSize', 10, ...
            'FontName', 'Calibri', ...
            'FontWeight', 'bold');
    end
end

title('Median Token Usage per Inference', ...
    'FontSize', 14, 'FontName', 'Calibri', 'FontWeight', 'bold');

set(ax, 'Box', 'on', 'LineWidth', 1.2);
ax.TickDir = 'out';
ax.TickLength = [0.00 0.00];

set(gcf, 'PaperUnits', 'inches');
set(gcf, 'PaperSize', [9 6]);
set(gcf, 'PaperPosition', [0 0 9 6]);
pause(0.1);
set(gca, 'Position', [0.13 0.18 0.82 0.75]);

print(gcf, [output_path, 'tokens_median_comparison.png'], '-dpng', '-r300');
print(gcf, [output_path, 'tokens_median_comparison.pdf'], '-dpdf', '-r300');
fprintf('Median token usage figure saved\n');

%% Figure: Individual Token Usage Over Data Points (1x3 Subplot Grid)
figure('Name', 'Tokens Over Data Points - Individual Models', 'Position', [100 100 1200 500]);
set(gcf, 'Color', 'white');

for model_idx = 1:num_models
    if isempty(token_data{model_idx})
        continue;
    end
    
    subplot(1, 3, model_idx);
    ax = gca();
    hold(ax, 'on');
    
    % Plot token usage over data points
    run_numbers = 1:length(token_data{model_idx});
    plot(ax, run_numbers, token_data{model_idx}, '-', 'Color', bar_colors(model_idx, :), ...
        'LineWidth', 1.2, 'DisplayName', 'Tokens Used');
    
    % Add horizontal line for median
    med_val = token_median(model_idx);
    yline(ax, med_val, '--b', 'LineWidth', 1.5, 'DisplayName', sprintf('Median: %.0f', med_val));
    
    % Styling
    ax.FontSize = 9;
    ax.FontName = 'Calibri';
    ax.XLabel.String = 'Data Point (with tokens)';
    ax.XLabel.FontSize = 10;
    ax.YLabel.String = 'Tokens Used';
    ax.YLabel.FontSize = 10;
    
    ax.YGrid = 'on';
    ax.GridLineStyle = '--';
    ax.GridAlpha = 0.3;
    ax.XGrid = 'off';
    
    title(sprintf('%s (n=%d)', model_names_cell{model_idx}, length(token_data{model_idx})), ...
        'FontSize', 11, 'FontName', 'Calibri', 'FontWeight', 'bold');
    
    legend('FontSize', 8, 'FontName', 'Calibri', 'Location', 'northwest', 'Box', 'off');
    
    set(ax, 'Box', 'on', 'LineWidth', 1);
    ax.TickDir = 'out';
end

sgtitle('Token Usage Over Data Points - All Models (Individual)', ...
    'FontSize', 14, 'FontName', 'Calibri', 'FontWeight', 'bold');

set(gcf, 'PaperUnits', 'inches');
set(gcf, 'PaperSize', [12 5]);
set(gcf, 'PaperPosition', [0 0 12 5]);
pause(0.1);

print(gcf, [output_path, 'tokens_over_datapoints_individual_subplots.png'], '-dpng', '-r300');
print(gcf, [output_path, 'tokens_over_datapoints_individual_subplots.pdf'], '-dpdf', '-r300');
fprintf('Individual token usage plots (1x3 subplot) saved\n');

%% Print Summary Statistics Table
fprintf('\n');
fprintf('===== COST STATISTICS SUMMARY =====\n');
fprintf('%-20s | %12s | %12s | %12s | %12s | %12s\n', ...
    'Model', 'Mean (USD)', 'Median (USD)', 'Std (USD)', 'Min (USD)', 'Max (USD)');
fprintf('%s\n', repmat('-', 85, 1));

for i = 1:num_models
    if cost_median(i) > 0
        fprintf('%-20s | %12.6f | %12.6f | %12.6f | %12.6f | %12.6f\n', ...
            model_names_cell{i}, cost_mean(i), cost_median(i), cost_std(i), cost_min(i), cost_max(i));
    end
end

fprintf('\nFigures saved to: %s\n', output_path);
fprintf('Figures: cost_median_comparison, tokens_median_comparison\n');
fprintf('Individual plots: tokens_over_datapoints_individual_subplots (1x3 grid)\n');
