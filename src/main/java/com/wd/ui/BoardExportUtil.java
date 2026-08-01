package com.wd.ui;

import com.intellij.openapi.diagnostic.Logger;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * 看板导出工具类：负责将 {@link KanbanBoard} 内容导出为 PDF / 图片。
 *
 * <p>从 {@link KanbanBoard} 中抽取，职责单一：</p>
 * <ul>
 *   <li>计算内容包围盒（{@link #calculateTotalBounds}）</li>
 *   <li>判断主题深色/浅色（{@link #isDarkTheme}）</li>
 *   <li>导出为 PDF（{@link #exportToPdf}）</li>
 *   <li>导出为图片（{@link #exportToImage}）</li>
 * </ul>
 *
 * <p>实际的内容绘制（{@code paintForExport}）因与 {@link KanbanBoard} 内部状态强耦合，
 * 仍保留在 {@link KanbanBoard} 内，本工具类通过 {@link KanbanBoard#paintForExport} 触发。</p>
 *
 * @author lww
 */
public final class BoardExportUtil {

	private static final Logger LOG = Logger.getInstance(BoardExportUtil.class);

	private BoardExportUtil() {
	}

	/**
	 * 导出边距（画板坐标像素）。
	 *
	 * <p>2026-08-01 需求变更：图片 / PDF 导出统一保留 {@value}px 边距，
	 * 避免内容紧贴图片边缘、视觉上不美观。</p>
	 */
	public static final int EXPORT_MARGIN = 20;

	/**
	 * 计算所有卡片（含阴影）的完整包围盒，用于导出范围。
	 *
	 * <p>无卡片时返回 {@code (0,0,1,1)} 最小尺寸。包围盒在画板坐标系下，
	 * 采用"围绕内容中心"的对称 padding（{@value #EXPORT_MARGIN}px），保证四边留白均匀。</p>
	 *
	 * @param cards 卡片列表
	 * @return 导出包围盒（画板坐标）
	 */
	public static Rectangle2D calculateTotalBounds(List<KanbanCard> cards) {
		if (cards == null || cards.isEmpty()) {
			return new Rectangle2D.Double(0, 0, 1, 1);
		}
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		for (KanbanCard card : cards) {
			Rectangle2D b = card.getBounds();
			if (b.getX() < minX) {
				minX = b.getX();
			}
			if (b.getY() < minY) {
				minY = b.getY();
			}
			if (b.getX() + b.getWidth() > maxX) {
				maxX = b.getX() + b.getWidth();
			}
			if (b.getY() + b.getHeight() > maxY) {
				maxY = b.getY() + b.getHeight();
			}
		}
		// 对称 padding：围绕内容中心留出 20px 边距，图片 / PDF 导出通用
		int padding = EXPORT_MARGIN;
		double contentCenterX = (minX + maxX) / 2.0;
		double contentCenterY = (minY + maxY) / 2.0;
		double halfW = (maxX - minX) / 2.0 + padding;
		double halfH = (maxY - minY) / 2.0 + padding;
		return new Rectangle2D.Double(contentCenterX - halfW, contentCenterY - halfH,
				halfW * 2, halfH * 2);
	}

	/**
	 * 根据面板背景亮度判断当前是否为深色主题（YIQ 亮度公式）。
	 *
	 * @param background 面板背景色
	 * @return true 表示深色主题
	 */
	public static boolean isDarkTheme(Color background) {
		if (background == null) {
			return false;
		}
		double brightness = (background.getRed() * 299
				+ background.getGreen() * 587
				+ background.getBlue() * 114) / 1000.0;
		return brightness < 128;
	}

	/**
	 * 导出当前看板为 PDF。
	 *
	 * <p>PDF 页面大小 = 卡片合并包围盒 + 4px padding。中文用 STSong-Light 字体
	 * （参考 DataHelper 实现）。无卡片时返回 false 让上层给出提示。</p>
	 *
	 * @param board 看板（提供卡片、连线及绘制入口）
	 * @param file  目标 PDF 文件
	 * @return 是否成功
	 */
	public static boolean exportToPdf(KanbanBoard board, File file) {
		if (board == null || file == null || board.getCards().isEmpty()) {
			return false;
		}
		Rectangle2D exportArea = calculateTotalBounds(board.getCards());
		LOG.warn("[DataChart export PDF] exportArea = x=" + (int) exportArea.getX()
				+ " y=" + (int) exportArea.getY()
				+ " w=" + (int) exportArea.getWidth()
				+ " h=" + (int) exportArea.getHeight()
				+ ", cards.size=" + board.getCards().size());
		float width = (float) exportArea.getWidth();
		float height = (float) exportArea.getHeight();
		if (width <= 0) {
			width = 800;
		}
		if (height <= 0) {
			height = 600;
		}

		com.itextpdf.text.Document document =
				new com.itextpdf.text.Document(new com.itextpdf.text.Rectangle(width, height));
		try {
			com.itextpdf.text.pdf.PdfWriter writer =
					com.itextpdf.text.pdf.PdfWriter.getInstance(document, new FileOutputStream(file));
			document.open();
			com.itextpdf.text.pdf.PdfContentByte cb = writer.getDirectContent();

			// 自定义字体映射器支持中文（参考 DataHelper）
			com.itextpdf.awt.DefaultFontMapper mapper = new com.itextpdf.awt.DefaultFontMapper() {
				@Override
				public com.itextpdf.text.pdf.BaseFont awtToPdf(java.awt.Font font) {
					try {
						return com.itextpdf.text.pdf.BaseFont.createFont(
								"STSong-Light", "UniGB-UCS2-H",
								com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED);
					} catch (Exception e) {
						try {
							return com.itextpdf.text.pdf.BaseFont.createFont(
									"C:/Windows/Fonts/simsun.ttc,0",
									com.itextpdf.text.pdf.BaseFont.IDENTITY_H,
									com.itextpdf.text.pdf.BaseFont.NOT_EMBEDDED);
						} catch (Exception ex) {
							return super.awtToPdf(font);
						}
					}
				}
			};

			java.awt.Graphics2D g2 = new com.itextpdf.awt.PdfGraphics2D(cb, width, height, mapper);
			try {
				board.paintForExport(g2, exportArea, isDarkTheme(board.getBackground()));
			} finally {
				g2.dispose();
			}
			document.close();
			return true;
		} catch (Exception ex) {
			LOG.warn("Export PDF failed: " + ex.getMessage(), ex);
			return false;
		}
	}

	/**
	 * 导出当前看板为图片（JPG / PNG）。
	 *
	 * <p>图片大小 = 卡片合并包围盒 * scale，1.0 表示原始尺寸。
	 * JPG 用高质量压缩（0.95f）。内存不足时自动降级 scale 防止 OOM。</p>
	 *
	 * @param board  看板（提供卡片、连线及绘制入口）
	 * @param file   目标图片文件
	 * @param format "jpg" 或 "png"
	 * @param scale  缩放倍数（1.0 = 原始）
	 * @return 是否成功
	 */
	public static boolean exportToImage(KanbanBoard board, File file, String format, double scale) {
		if (board == null || file == null || format == null || board.getCards().isEmpty()) {
			return false;
		}
		if (scale <= 0) {
			scale = 1.0;
		}

		Rectangle2D exportArea = calculateTotalBounds(board.getCards());
		int targetW = Math.max(1, (int) (exportArea.getWidth() * scale));
		int targetH = Math.max(1, (int) (exportArea.getHeight() * scale));
		// debug：打印 exportArea 实际值，方便诊断"图片没居中"问题
		StringBuilder cardInfo = new StringBuilder();
		for (KanbanCard c : board.getCards()) {
			cardInfo.append("\n  card ").append(c.getId()).append(" bounds=")
					.append((int) c.getBounds().getX()).append(",")
					.append((int) c.getBounds().getY()).append(" ")
					.append((int) c.getBounds().getWidth()).append("x")
					.append((int) c.getBounds().getHeight());
		}
		LOG.warn("[DataChart export] cards.size=" + board.getCards().size() + cardInfo
				+ "\n  exportArea = x=" + (int) exportArea.getX()
				+ " y=" + (int) exportArea.getY()
				+ " w=" + (int) exportArea.getWidth()
				+ " h=" + (int) exportArea.getHeight()
				+ ", target=" + targetW + "x" + targetH
				+ ", scale=" + scale);

		// 内存管理：如果目标过大，自动降级 scale
		long freeMemory = Runtime.getRuntime().maxMemory()
				- Runtime.getRuntime().totalMemory() + Runtime.getRuntime().freeMemory();
		long estimatedBytes = (long) targetW * targetH * 4L;
		if (estimatedBytes > freeMemory * 0.6) {
			double safetyScale = Math.sqrt((freeMemory * 0.5)
					/ (exportArea.getWidth() * exportArea.getHeight() * 4));
			scale = Math.min(scale, safetyScale);
			if (scale < 0.1) {
				scale = 0.1;
			}
			targetW = Math.max(1, (int) (exportArea.getWidth() * scale));
			targetH = Math.max(1, (int) (exportArea.getHeight() * scale));
			LOG.warn("Export area too large, scale auto-adjusted to " + scale);
		}

		// JPG 不支持透明，用 RGB；PNG 用 ARGB
		int imageType = "png".equalsIgnoreCase(format)
				? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
		BufferedImage img = new BufferedImage(targetW, targetH, imageType);
		java.awt.Graphics2D g2 = img.createGraphics();
		try {
			board.paintForExport(g2, exportArea,
					isDarkTheme(board.getBackground()), scale, targetW, targetH);
		} finally {
			g2.dispose();
		}

		try (FileOutputStream fos = new FileOutputStream(file)) {
			if ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format)) {
				javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
				try (javax.imageio.stream.ImageOutputStream ios =
						ImageIO.createImageOutputStream(fos)) {
					writer.setOutput(ios);
					javax.imageio.plugins.jpeg.JPEGImageWriteParam jpegParams =
							(javax.imageio.plugins.jpeg.JPEGImageWriteParam) writer.getDefaultWriteParam();
					jpegParams.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
					jpegParams.setCompressionQuality(0.95f);
					writer.write(null,
							new javax.imageio.IIOImage(img, null, null), jpegParams);
				} finally {
					writer.dispose();
				}
			} else {
				ImageIO.write(img, format, fos);
			}
			img.flush();
			return true;
		} catch (Exception ex) {
			LOG.warn("Export image failed: " + ex.getMessage(), ex);
			return false;
		}
	}
}
