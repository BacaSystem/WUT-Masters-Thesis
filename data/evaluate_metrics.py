#!/usr/bin/env python3
"""
Script to evaluate image caption metrics (BLEU, CIDEr, SPICE, METEOR)
for multiple models using COCO evaluation toolkit.

Data is read from CSV files. For each unique image_id in the CSVs,
we use reference captions from COCO validation set and compare
model's captions against them.
"""

import os
import json
import pandas as pd
import sys
from pathlib import Path
from collections import defaultdict

# Add metrics_repo to path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'metrics_repo'))

from pycocotools.coco import COCO
from pycocoevalcap.eval import COCOEvalCap


def load_ground_truth(coco_annotation_file):
    """
    Load ground truth captions from COCO annotation file.
    Maps image_id to list of reference captions.
    """
    print(f"Loading ground truth captions from {coco_annotation_file}...")
    
    with open(coco_annotation_file, 'r') as f:
        data = json.load(f)
    
    # Create mapping from image_id to captions
    image_captions = defaultdict(list)
    for annotation in data['annotations']:
        image_id = annotation['image_id']
        caption = annotation['caption']
        image_captions[image_id].append(caption)
    
    print(f"Loaded reference captions for {len(image_captions)} images")
    return image_captions


def csv_to_results(csv_file):
    """
    Convert CSV results to dict format.
    
    Returns dict mapping image_id -> list of captions
    """
    df = pd.read_csv(csv_file)
    
    results = defaultdict(list)
    for _, row in df.iterrows():
        image_id = int(row['image_id'])
        caption = str(row['caption_text']).strip()
        results[image_id].append(caption)
    
    return results


def create_coco_annotations(image_captions):
    """
    Create COCO format annotations from image_captions dict.
    
    Returns dict with 'images' and 'annotations' keys.
    """
    images = []
    annotations = []
    
    ann_id = 0
    for image_id, captions in sorted(image_captions.items()):
        images.append({"id": image_id})
        for caption in captions:
            annotations.append({
                "id": ann_id,
                "image_id": image_id,
                "caption": caption
            })
            ann_id += 1
    
    return {
        "images": images,
        "annotations": annotations
    }


def create_coco_results_format(results_dict):
    """
    Convert results dict to COCO results format.
    Takes first caption per image_id.
    
    Returns list of dicts with image_id and caption.
    """
    results = []
    for image_id, captions in sorted(results_dict.items()):
        if captions:
            # Take the first caption
            results.append({
                "image_id": image_id,
                "caption": captions[0]
            })
    return results


def evaluate_model(csv_file, ground_truth_captions, output_dir):
    """
    Evaluate a single model's results.
    
    Returns dict with metric scores.
    """
    model_name = Path(csv_file).stem
    print(f"\nEvaluating {model_name}...")
    
    # Convert CSV to results format
    results_dict = csv_to_results(csv_file)
    
    # Get only images that have both ground truth and predictions
    valid_image_ids = set(ground_truth_captions.keys()) & set(results_dict.keys())
    
    if not valid_image_ids:
        print(f"  ERROR: No common images between ground truth and predictions")
        return None
    
    print(f"  Found {len(valid_image_ids)} images with both ground truth and predictions")
    
    # DEBUG: Log info for image 285
    if 285 in valid_image_ids:
        print(f"\n  [DEBUG IMAGE 285]")
        print(f"    Ground truth captions ({len(ground_truth_captions[285])}):")
        for i, cap in enumerate(ground_truth_captions[285], 1):
            print(f"      [{i}] {cap}")
        print(f"    Model predictions ({len(results_dict[285])}):")
        for i, cap in enumerate(results_dict[285], 1):
            print(f"      [{i}] {cap}")
        print(f"  [/DEBUG]")
    
    # Create filtered ground truth and results for evaluation
    filtered_gt = {img_id: ground_truth_captions[img_id] for img_id in valid_image_ids}
    filtered_results = {img_id: results_dict[img_id] for img_id in valid_image_ids}
    
    # Create COCO format annotations
    gt_coco_format = create_coco_annotations(filtered_gt)
    results_coco_format = create_coco_results_format(filtered_results)
    
    # DEBUG: Log COCO format data for image 285
    if 285 in filtered_gt or 285 in filtered_results:
        print(f"\n  [DEBUG IMAGE 285 - COCO FORMAT]")
        
        # Find image 285 in gt_coco_format
        for img in gt_coco_format['images']:
            if img['id'] == 285:
                print(f"    GT Image found: {img}")
                break
        
        # Find annotations for image 285
        gt_anns_285 = [ann for ann in gt_coco_format['annotations'] if ann['image_id'] == 285]
        print(f"    GT Annotations count: {len(gt_anns_285)}")
        for ann in gt_anns_285:
            print(f"      {ann}")
        
        # Find results for image 285
        res_285 = [res for res in results_coco_format if res['image_id'] == 285]
        print(f"    Results count: {len(res_285)}")
        for res in res_285:
            print(f"      {res}")
        
        print(f"  [/DEBUG]")
    
    # Save temporary files
    gt_file = os.path.join(output_dir, f"_temp_gt_{model_name}.json")
    res_file = os.path.join(output_dir, f"_temp_res_{model_name}.json")
    
    with open(gt_file, 'w') as f:
        json.dump(gt_coco_format, f)
    
    with open(res_file, 'w') as f:
        json.dump(results_coco_format, f)
    
    # DEBUG: Log saved files for image 285
    if 285 in filtered_gt or 285 in filtered_results:
        print(f"\n  [DEBUG IMAGE 285 - SAVED FILES]")
        print(f"    GT file: {gt_file}")
        print(f"    Results file: {res_file}")
        
        # Verify by re-reading
        with open(gt_file, 'r') as f:
            gt_verify = json.load(f)
        with open(res_file, 'r') as f:
            res_verify = json.load(f)
        
        gt_anns_285_verify = [ann for ann in gt_verify['annotations'] if ann['image_id'] == 285]
        res_285_verify = [res for res in res_verify if res['image_id'] == 285]
        
        print(f"    Verified GT annotations for 285: {len(gt_anns_285_verify)}")
        print(f"    Verified results for 285: {len(res_285_verify)}")
        print(f"  [/DEBUG]")
    
    # Create COCO objects
    coco_gt = COCO(gt_file)
    coco_res = coco_gt.loadRes(res_file)
    
    # Create evaluator
    coco_eval = COCOEvalCap(coco_gt, coco_res)
    
    # Evaluate
    print(f"  Computing metrics...")
    coco_eval.evaluate()
    
    # Extract scores
    scores = dict(coco_eval.eval)
    
    print(f"  Results for {model_name}:")
    for metric, score in scores.items():
        print(f"    {metric}: {score:.4f}")
    
    # Clean up temporary files
    os.remove(gt_file)
    os.remove(res_file)
    
    return {
        "model_name": model_name,
        "num_images": len(valid_image_ids),
        "num_captions": len(results_coco_format),
        "metrics": scores
    }


def main():
    """Main evaluation pipeline."""
    base_dir = os.path.dirname(os.path.abspath(__file__))
    
    # Paths
    csv_dir = os.path.join(base_dir, 'csv')
    annotations_file = os.path.join(base_dir, 'coco', 'captions_val2017.json')
    output_dir = base_dir
    
    print("="*80)
    print("COCO Caption Evaluation Pipeline")
    print("="*80)
    
    # Check if annotations file exists
    if not os.path.exists(annotations_file):
        print(f"ERROR: Annotations file not found: {annotations_file}")
        return
    
    # Load ground truth captions
    ground_truth_captions = load_ground_truth(annotations_file)
    
    # Find all CSV files in the data/csv directory
    csv_files = sorted([f for f in os.listdir(csv_dir) if f.endswith('.csv')])
    
    if not csv_files:
        print(f"No CSV files found in {csv_dir}")
        return
    
    print(f"\nFound {len(csv_files)} CSV files to evaluate:")
    for f in csv_files:
        print(f"  - {f}")
    
    # Evaluate each model
    results = {}
    for csv_file in csv_files:
        csv_path = os.path.join(csv_dir, csv_file)
        result = evaluate_model(csv_path, ground_truth_captions, output_dir)
        if result:
            results[result['model_name']] = result
    
    # Save results summary
    results_file = os.path.join(output_dir, 'evaluation_results.json')
    with open(results_file, 'w') as f:
        json.dump(results, f, indent=2)
    
    print(f"\n" + "="*80)
    print("SUMMARY OF RESULTS")
    print("="*80)
    print(f"Results saved to: {results_file}\n")
    
    # Print summary table
    print(f"{'Model':<40} {'Images':<8} {'BLEU':<10} {'CIDEr':<10} {'SPICE':<10} {'METEOR':<10}")
    print("-"*88)
    
    for model_name in sorted(results.keys()):
        result = results[model_name]
        metrics = result['metrics']
        num_images = result['num_images']
        
        bleu = metrics.get('Bleu_4', 0)  # Use Bleu_4 instead of 'Bleu'
        cider = metrics.get('CIDEr', 0)
        spice = metrics.get('SPICE', 0)
        meteor = metrics.get('METEOR', 0)
        
        print(f"{model_name:<40} {num_images:<8} {bleu:>9.4f} {cider:>9.4f} {spice:>9.4f} {meteor:>9.4f}")
    
    print("="*88)
    print("\nMetric Explanation:")
    print("  BLEU   - Bilingual Evaluation Understudy (matches n-grams, scale 0-1)")
    print("  CIDEr  - Consensus-based Image Description Evaluation")
    print("  SPICE  - Semantic Propositional Image Caption Evaluation")
    print("  METEOR - Metric for Evaluation of Translation with Explicit ORdering")
    print("="*88)


if __name__ == '__main__':
    main()
