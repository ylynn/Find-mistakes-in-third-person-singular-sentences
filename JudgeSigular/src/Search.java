import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;

public class Search {
	private static String parserModel = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
	private static String[] options = { "-maxLength", "80",
			"-retainTmpSubcategories" };
	private static String[] VBList = { "VB", "VBD", "VBG", "VBN", "VBP", "VBZ" };
	private static String[] NNList = { "PRP", "PRP$", "NN", "NNS", "NNP",
			"NNPS", "DT" };
	private static String[] ThirdList = { "he", "she", "it", "this", "that" };
	private static String[] beList = { "is", "am", "are" };
	private static LexicalizedParser lp;
	private static TreebankLanguagePack tlp;

	public static void main(String[] args) throws IOException {
		// 使用命令行参数，获取输入输出文件名
		if (args.length != 2) {
			System.out
					.println("Please run the program as follow : java search test.input test.output");
		} else {
			// 初始化语法处理器
			lp = LexicalizedParser.loadModel(parserModel, options);
			tlp = lp.getOp().langpack();

			// 根据输出文件名创建输入流
			BufferedReader br = new BufferedReader(new FileReader(new File(
					args[0])));

			// 根据输出文件名创建输出流
			BufferedWriter bf = new BufferedWriter(new FileWriter(new File(
					args[1])));

			String oneline;
			while ((oneline = br.readLine()) != null) {
				bf.write(searchWrong(oneline));
				bf.newLine();
			}

			br.close();
			bf.close();
		}
	}

	// 对每一个句子进行处理
	private static String searchWrong(String oneline) {
		String toReturn = oneline;
		Tokenizer<? extends HasWord> toke = tlp.getTokenizerFactory()
				.getTokenizer(new StringReader(oneline));
		List<? extends HasWord> sentence = toke.tokenize();
		Tree parse = lp.parse(sentence);

		// 打印出parse树
		parse.pennPrint();
		System.out.println();

		// 找到句子中的从句
		List<Tree> subs = subs(parse, null);

		// 用于存放错误的树叶节点
		List<Tree> wrongs = new ArrayList<Tree>();

		// 对每个从句进行处理，并暂存返回的错误代码
		for (Tree tree : subs) {
			tree.pennPrint();
			Tree temp = judgeSingular(tree);
			if (temp != null) {
				wrongs.add(temp);
			}
		}

		String num = "";
		if (wrongs.size() == 0) {
			num = "-1 ";
		} else {
			List<Tree> leaves = parse.getLeaves();
			for (Tree leaf : wrongs) {
				for (int i = 0; i < leaves.size(); i++) {
					if (leaves.get(i) == leaf) {
						num = num + (i + 1) + " ";
						break;
					}
				}
			}
		}

		toReturn = num + toReturn;

		// 打印检查结果
		System.out.println(toReturn);
		return toReturn;
	}

	// 该方法应该有更优雅的写法。在一次遍历中，即可将所有需要检查第三人称的S,SBAR,SBARQ,SINV等找出
	private static List<Tree> subs(Tree tree, Tree parent) {
		List<Tree> toReturn = new ArrayList<Tree>();
		if (tree.isLeaf()) {
			return toReturn;
		} else {
			if (tree.value().equals("S")) {// 此处可添加其他句型的判断
				toReturn.add(tree);
			} else if (tree.value().equals("SBAR")) {
				toReturn.add(parent);
			}
			for (int i = 0; i < tree.getChildrenAsList().size(); i++) {
				toReturn.addAll(subs(tree.getChildrenAsList().get(i), tree));
			}
			return toReturn;
		}
	}

	// 判断从句中是否存在第三人称单数错误
	private static Tree judgeSingular(Tree tree) {
		Tree toReturn = null;
		if (tree.value().equals("S")) {
			toReturn = judgeForS(tree);
		} else {// 此处可添加其他句型的判断
			toReturn = judgeForSBAR(tree);
		}
		return toReturn;
	}

	// 对于一般陈述句
	private static Tree judgeForS(Tree tree) {
		Tree toReturn = null;
		Tree[] children = tree.children();
		Tree np = null;
		Tree vp = null;
		Tree nn = null;
		Tree vb = null;
		// 找到np和vp,md
		for (int i = 0; i < children.length; i++) {
			if (children[i].value().equals("NP")) {
				np = children[i];
			}
			if (children[i].value().equals("VP")) {
				vp = children[i];
			}
		}
		// 如果两者为空，则说明句子不完整，直接返回
		if (np == null || vp == null) {
			return null;
		}
		// 找到np中的NN
		nn = findNN(np);
		// 找到vp中的VB
		for (int i = 0; i < vp.children().length; i++) {
			Tree temp = vp.children()[i];
			if (vb != null) {
				break;
			}
			for (String m : VBList) {
				if (temp.value().equals(m)) {
					vb = temp;
					break;
				}
			}
		}

		// 出去情态动词和TO的情况，以及矩形不对的情况
		if (vp.firstChild().value().equals("MD")
				|| vp.firstChild().value().equals("TO") || vb == null
				|| nn == null) {
			return null;
		}
		if (isSingleNN(nn) != isSingleVB(vb) && !isOther(vb)) {
			toReturn = vb;
		} else {
			// 在此处考虑第一人称为主语的情况
			if (nn.firstChild().value().toLowerCase().equals("i") != vb
					.firstChild().value().toLowerCase().equals("am")
					&& isBe(vb.firstChild())) {
				toReturn = vb;
			}
		}
		if (toReturn != null) {
			toReturn = toReturn.firstChild();
		}
		return toReturn;
	}

	private static boolean isBe(Tree tree) {
		String temp = tree.value().toLowerCase();
		for (String s : beList) {
			if (s.equals(temp)) {
				return true;
			}
		}
		return false;
	}

	// 找到np从句中的nn
	private static Tree findNN(Tree np) {
		Tree nn = null;
		for (int i = 0; i < np.children().length; i++) {
			Tree temp = np.children()[i];
			if (nn != null) {
				break;
			}
			// 遍历np下每个单词，看是否在nnlist中，从而找出nn
			for (String m : NNList) {
				if (temp.value().equals(m)) {
					// 当前nn为the，无法判断nn的单复数时，看后一位
					if (temp.firstChild().value().toLowerCase().equals("the")) {
						continue;
					}
					nn = temp;
					break;
				}
			}
		}
		if (nn == null) {
			// 当没有nn而np下还有np时，继续寻找np下的np中存在的nn
			for (int i = 0; i < np.children().length; i++) {
				Tree temp = np.children()[i];
				if (nn != null) {
					break;
				}
				if (temp.value().equals("NP")) {
					nn = findNN(temp);
				}
			}
		}
		return nn;
	}

	// 判断单复数和人称
	private static boolean isSingleNN(Tree nn) {
		String value = nn.value();
		if (value.equals("NN") || value.equals("NNP")
				|| isThird(nn.firstChild().value())) {
			return true;
		}
		return false;
	}

	// 判断动词形式
	private static boolean isSingleVB(Tree vb) {
		String value = vb.value();
		if (value.equals("VBZ")) {
			return true;
		}
		return false;
	}

	// 对于人称代词，是否为第三人称
	private static boolean isThird(String toCheck) {
		toCheck = toCheck.toLowerCase();
		for (String m : ThirdList) {
			if (toCheck.equals(m)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isOther(Tree tree) {
		if (tree.value().equals("VBD") || tree.value().equals("VBG")
				|| tree.value().equals("VBN")) {
			return true;
		}
		return false;
	}

	// 对于从属词引导从句
	private static Tree judgeForSBAR(Tree tree) {
		Tree toReturn = null;
		Tree[] children = tree.children();
		Tree sbar = null;
		Tree np = null;
		Tree vp = null;
		Tree nn = null;
		Tree vb = null;
		// 找到np和vp,md
		for (int i = 0; i < children.length; i++) {
			if (children[i].value().equals("NP")) {
				np = children[i];
			}
			if (children[i].value().equals("SBAR")) {
				sbar = children[i];
			}
		}
		// 如果两者为空，则说明句子不完整，直接返回
		if (np == null || sbar == null) {
			return null;
		}
		// 找到np中的NN
		nn = findNN(np);

		// 找到sbar下的vp
		vp = findVP(sbar);
		if (vp == null) {
			return null;
		}

		// 找到vp中的vb
		for (int i = 0; i < vp.children().length; i++) {
			Tree temp = vp.children()[i];
			if (vb != null) {
				break;
			}
			for (String m : VBList) {
				if (temp.value().equals(m)) {
					vb = temp;
					break;
				}
			}
		}

		// 出去情态动词和TO的情况，以及矩形不对的情况
		if (vp.firstChild().value().equals("MD")
				|| vp.firstChild().value().equals("TO") || vb == null
				|| nn == null) {
			return null;
		}

		if (isSingleNN(nn) != isSingleVB(vb) && !isOther(vb)) {
			toReturn = vb;
		} else {
			// 在此处考虑第一人称为主语的情况
			if (nn.firstChild().value().toLowerCase().equals("i") != vb
					.firstChild().value().toLowerCase().equals("am")
					&& isBe(vb.firstChild())) {
				toReturn = vb;
			}
		}
		if (toReturn != null) {
			toReturn = toReturn.firstChild();
		}

		return toReturn;
	}

	private static Tree findVP(Tree sbar) {
		Tree vp = null;
		for (int i = 0; i < sbar.children().length; i++) {
			Tree temp = sbar.children()[i];
			if (temp.value().equals("VP")) {
				vp = temp;
			}
		}
		if (vp == null) {
			for (int i = 0; i < sbar.children().length; i++) {
				Tree temp = sbar.children()[i];
				if (!temp.isLeaf()) {
					Tree result = findVP(temp);
					if (result != null) {
						vp = result;
						break;
					}
				}
			}
		}
		return vp;
	}
}
