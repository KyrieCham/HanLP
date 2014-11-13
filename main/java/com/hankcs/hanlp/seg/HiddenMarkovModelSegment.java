/*
 * <summary></summary>
 * <author>He Han</author>
 * <email>hankcs.cn@gmail.com</email>
 * <create-date>2014/10/30 10:02</create-date>
 *
 * <copyright file="HiddenMarkovModelSegment.java" company="上海林原信息科技有限公司">
 * Copyright (c) 2003-2014, 上海林原信息科技有限公司. All Right Reserved, http://www.linrunsoft.com/
 * This source is subject to the LinrunSpace License. Please contact 上海林原信息科技有限公司 to get more information.
 * </copyright>
 */
package com.hankcs.hanlp.seg;

import com.hankcs.hanlp.corpus.tag.Nature;
import com.hankcs.hanlp.dictionary.BaseSearcher;
import com.hankcs.hanlp.dictionary.CoreDictionary;
import com.hankcs.hanlp.dictionary.CustomDictionary;
import com.hankcs.hanlp.seg.NShort.Path.*;
import com.hankcs.hanlp.seg.common.Graph;
import com.hankcs.hanlp.seg.common.Term;
import com.hankcs.hanlp.seg.common.Vertex;
import com.hankcs.hanlp.seg.common.WordNet;
import com.hankcs.hanlp.utility.TextUtility;
import com.hankcs.hanlp.utility.Predefine;

import java.util.*;

/**
 * 隐马模型分词器基类
 * @author hankcs
 */
public abstract class HiddenMarkovModelSegment extends AbstractSegment
{
    protected Config config;

    public HiddenMarkovModelSegment()
    {
        config = new Config();
    }

    /**
     * 对粗分结果执行一些规则上的合并拆分等等，同时合成新词网
     *
     * @param linkedArray    粗分结果
     * @param wordNetOptimum 合并了所有粗分结果的词网
     */
    protected static void GenerateWord(List<Vertex> linkedArray, WordNet wordNetOptimum)
    {
        fixResultByRule(linkedArray);

        //--------------------------------------------------------------------
        // 建造新词网
        wordNetOptimum.addAll(linkedArray);
    }

    /**
     * 通过规则修正一些结果
     * @param linkedArray
     */
    protected static void fixResultByRule(List<Vertex> linkedArray)
    {

        //--------------------------------------------------------------------
        //Merge all seperate continue num into one number
        MergeContinueNumIntoOne(linkedArray);

        //--------------------------------------------------------------------
        //The delimiter "－－"
        ChangeDelimiterPOS(linkedArray);

        //--------------------------------------------------------------------
        //如果前一个词是数字，当前词以“－”或“-”开始，并且不止这一个字符，
        //那么将此“－”符号从当前词中分离出来。
        //例如 “3 / -4 / 月”需要拆分成“3 / - / 4 / 月”
        SplitMiddleSlashFromDigitalWords(linkedArray);

        //--------------------------------------------------------------------
        //1、如果当前词是数字，下一个词是“月、日、时、分、秒、月份”中的一个，则合并,且当前词词性是时间
        //2、如果当前词是可以作为年份的数字，下一个词是“年”，则合并，词性为时间，否则为数字。
        //3、如果最后一个汉字是"点" ，则认为当前数字是时间
        //4、如果当前串最后一个汉字不是"∶·．／"和半角的'.''/'，那么是数
        //5、当前串最后一个汉字是"∶·．／"和半角的'.''/'，且长度大于1，那么去掉最后一个字符。例如"1."
        CheckDateElements(linkedArray);

    }

    static void ChangeDelimiterPOS(List<Vertex> linkedArray)
    {
        for (Vertex vertex : linkedArray)
        {
            if (vertex.realWord.equals("－－") || vertex.realWord.equals("—") || vertex.realWord.equals("-"))
            {
                vertex.confirmNature(Nature.w);
            }
        }
    }

    //====================================================================
    //如果前一个词是数字，当前词以“－”或“-”开始，并且不止这一个字符，
    //那么将此“－”符号从当前词中分离出来。
    //例如 “3-4 / 月”需要拆分成“3 / - / 4 / 月”
    //====================================================================
    private static void SplitMiddleSlashFromDigitalWords(List<Vertex> linkedArray)
    {
        if (linkedArray.size() < 2)
            return;

        ListIterator<Vertex> listIterator = linkedArray.listIterator();
        Vertex next = listIterator.next();
        Vertex current = next;
        while (listIterator.hasNext())
        {
            next = listIterator.next();
//            System.out.println("current:" + current + " next:" + next);
            Nature currentNature = current.getNature();
            if (currentNature == Nature.nx && (next.hasNature(Nature.q) || next.hasNature(Nature.n)))
            {
                String[] param = current.realWord.split("-", 1);
                if (param.length == 2)
                {
                    if (TextUtility.isAllNum(param[0]) && TextUtility.isAllNum(param[1]))
                    {
                        current = current.copy();
                        current.realWord = param[0];
                        current.confirmNature(Nature.m);
                        listIterator.previous();
                        listIterator.previous();
                        listIterator.set(current);
                        listIterator.next();
                        listIterator.add(Vertex.newPunctuationInstance("-"));
                        listIterator.add(Vertex.newNumberInstance(param[1]));
                    }
                }
            }
            current = next;
        }

//        logger.trace("杠号识别后：" + Graph.parseResult(linkedArray));
    }

    //====================================================================
    //1、如果当前词是数字，下一个词是“月、日、时、分、秒、月份”中的一个，则合并且当前词词性是时间
    //2、如果当前词是可以作为年份的数字，下一个词是“年”，则合并，词性为时间，否则为数字。
    //3、如果最后一个汉字是"点" ，则认为当前数字是时间
    //4、如果当前串最后一个汉字不是"∶·．／"和半角的'.''/'，那么是数
    //5、当前串最后一个汉字是"∶·．／"和半角的'.''/'，且长度大于1，那么去掉最后一个字符。例如"1."
    //====================================================================
    private static void CheckDateElements(List<Vertex> linkedArray)
    {
        if (linkedArray.size() < 2)
            return;
        ListIterator<Vertex> listIterator = linkedArray.listIterator();
        Vertex next = listIterator.next();
        Vertex current = next;
        while (listIterator.hasNext())
        {
            next = listIterator.next();
            if (TextUtility.isAllNum(current.realWord) || TextUtility.isAllChineseNum(current.realWord))
            {
                //===== 1、如果当前词是数字，下一个词是“月、日、时、分、秒、月份”中的一个，则合并且当前词词性是时间
                String nextWord = next.realWord;
                if ((nextWord.length() == 1 && "月日时分秒".contains(nextWord)) || (nextWord.length() == 2 && nextWord.equals("月份")))
                {
                    current = Vertex.newTimeInstance(current.realWord + next.realWord);
                    listIterator.previous();
                    listIterator.previous();
                    listIterator.set(current);
                    listIterator.next();
                    listIterator.next();
                    listIterator.remove();
                }
                //===== 2、如果当前词是可以作为年份的数字，下一个词是“年”，则合并，词性为时间，否则为数字。
                else if (nextWord.equals("年"))
                {
                    if (TextUtility.isYearTime(current.realWord))
                    {
                        current = Vertex.newTimeInstance(current.realWord + next.realWord);
                        listIterator.previous();
                        listIterator.previous();
                        listIterator.set(current);
                        listIterator.next();
                        listIterator.next();
                        listIterator.remove();
                    }
                    //===== 否则当前词就是数字了 =====
                    else
                    {
                        current.confirmNature(Nature.m);
                    }
                }
                else
                {
                    //===== 3、如果最后一个汉字是"点" ，则认为当前数字是时间
                    if (current.realWord.endsWith("点"))
                    {
                        current.confirmNature(Nature.t, true);
                    }
                    else
                    {
                        char[] tmpCharArray = current.realWord.toCharArray();
                        String lastChar = String.valueOf(tmpCharArray[tmpCharArray.length - 1]);
                        //===== 4、如果当前串最后一个汉字不是"∶·．／"和半角的'.''/'，那么是数
                        if (!"∶·．／./".contains(lastChar))
                        {
                            current.confirmNature(Nature.m, true);
                        }
                        //===== 5、当前串最后一个汉字是"∶·．／"和半角的'.''/'，且长度大于1，那么去掉最后一个字符。例如"1."
                        else if (current.realWord.length() > 1)
                        {
                            char last = current.realWord.charAt(current.realWord.length() - 1);
                            current = Vertex.newNumberInstance(current.realWord.substring(0, current.realWord.length() - 1));
                            listIterator.previous();
                            listIterator.previous();
                            listIterator.set(current);
                            listIterator.next();
                            listIterator.add(Vertex.newPunctuationInstance(String.valueOf(last)));
                        }
                    }
                }
            }
            current = next;
        }
//        logger.trace("日期识别后：" + Graph.parseResult(linkedArray));
    }

    /**
     * 将一条路径转为最终结果
     *
     * @param vertexList
     * @return
     */
    protected static List<Term> convert(List<Vertex> vertexList)
    {
        assert vertexList != null;
        assert vertexList.size() >= 2 : "这条路径不应当短于2" + vertexList.toString();
        int length = vertexList.size() - 2;
        List<Term> resultList = new ArrayList<Term>(length);
        Iterator<Vertex> iterator = vertexList.iterator();
        iterator.next();
        int offset = 0;
        for (int i = 0; i < length; ++i)
        {
            Vertex vertex = iterator.next();
            Term term = convert(vertex);
            term.offset = offset;
            offset += term.length();
            resultList.add(term);
        }
        return resultList;
    }

    /**
     * 生成二元词图
     *
     * @param wordNet
     * @return
     */
    protected static Graph GenerateBiGraph(WordNet wordNet)
    {
        return wordNet.toGraph();
    }

    /**
     * 原子分词
     * @param charArray
     * @param start 从start开始（包含）
     * @param end 到end结束（不包含end）
     * @return
     */
    private static List<AtomNode> AtomSegment(char[] charArray, int start, int end)
    {
        if (end < start)
        {
            throw new RuntimeException("start=" + start + " < end=" + end);
        }
        List<AtomNode> atomSegment = new ArrayList<AtomNode>();
        int pCur = start, nCurType, nNextType;
        StringBuilder sb = new StringBuilder();
        char c;


        //==============================================================================================
        // by zhenyulu:
        //
        // TODO: 使用一系列正则表达式将句子中的完整成分（百分比、日期、电子邮件、URL等）预先提取出来
        //==============================================================================================

        int[] charTypeArray = new int[end - start];

        // 生成对应单个汉字的字符类型数组
        for (int i = 0; i < charTypeArray.length; ++i)
        {
            c = charArray[i + start];
            charTypeArray[i] = TextUtility.charType(c);

            if (c == '.' && i  + start < (charArray.length - 1) && TextUtility.charType(charArray[i + start + 1]) == Predefine.CT_NUM)
                charTypeArray[i] = Predefine.CT_NUM;
            else if (c == '.' && i  + start < (charArray.length - 1) && charArray[i  + start + 1] >= '0' && charArray[i  + start + 1] <= '9')
                charTypeArray[i] = Predefine.CT_SINGLE;
            else if (charTypeArray[i] == Predefine.CT_LETTER)
                charTypeArray[i] = Predefine.CT_SINGLE;
        }

        // 根据字符类型数组中的内容完成原子切割
        while (pCur < end)
        {
            nCurType = charTypeArray[pCur - start];

            if (nCurType == Predefine.CT_CHINESE || nCurType == Predefine.CT_INDEX ||
                    nCurType == Predefine.CT_DELIMITER || nCurType == Predefine.CT_OTHER)
            {
                String single = String.valueOf(charArray[pCur]);
                if (single.length() != 0)
                    atomSegment.add(new AtomNode(single, nCurType));
                pCur++;
            }
            //如果是字符、数字或者后面跟随了数字的小数点“.”则一直取下去。
            else if (pCur < end - 1 && ((nCurType == Predefine.CT_SINGLE) || nCurType == Predefine.CT_NUM))
            {
                sb.delete(0, sb.length());
                sb.append(charArray[pCur]);

                boolean reachEnd = true;
                while (pCur < end - 1)
                {
                    nNextType = charTypeArray[++pCur - start];

                    if (nNextType == nCurType)
                        sb.append(charArray[pCur]);
                    else
                    {
                        reachEnd = false;
                        break;
                    }
                }
                atomSegment.add(new AtomNode(sb.toString(), nCurType));
                if (reachEnd)
                    pCur++;
            }
            // 对于所有其它情况
            else
            {
                atomSegment.add(new AtomNode(charArray[pCur], nCurType));
                pCur++;
            }
        }

//        logger.trace("原子分词:" + atomSegment);
        return atomSegment;
    }

    /**
     * 原子分词
     * @deprecated 应该使用字符数组的版本
     * @param sSentence
     * @param start
     * @param end
     * @return
     */
    private static List<AtomNode> AtomSegment(String sSentence, int start, int end)
    {
        if (end < start)
        {
            throw new RuntimeException("start=" + start + " < end=" + end);
        }
        List<AtomNode> atomSegment = new ArrayList<AtomNode>();
        int pCur = 0, nCurType, nNextType;
        StringBuilder sb = new StringBuilder();
        char c;


        //==============================================================================================
        // by zhenyulu:
        //
        // TODO: 使用一系列正则表达式将句子中的完整成分（百分比、日期、电子邮件、URL等）预先提取出来
        //==============================================================================================

        char[] charArray = sSentence.substring(start, end).toCharArray();
        int[] charTypeArray = new int[charArray.length];

        // 生成对应单个汉字的字符类型数组
        for (int i = 0; i < charArray.length; ++i)
        {
            c = charArray[i];
            charTypeArray[i] = TextUtility.charType(c);

            if (c == '.' && i < (charArray.length - 1) && TextUtility.charType(charArray[i + 1]) == Predefine.CT_NUM)
                charTypeArray[i] = Predefine.CT_NUM;
            else if (c == '.' && i < (charArray.length - 1) && charArray[i + 1] >= '0' && charArray[i + 1] <= '9')
                charTypeArray[i] = Predefine.CT_SINGLE;
            else if (charTypeArray[i] == Predefine.CT_LETTER)
                charTypeArray[i] = Predefine.CT_SINGLE;
        }

        // 根据字符类型数组中的内容完成原子切割
        while (pCur < charArray.length)
        {
            nCurType = charTypeArray[pCur];

            if (nCurType == Predefine.CT_CHINESE || nCurType == Predefine.CT_INDEX ||
                    nCurType == Predefine.CT_DELIMITER || nCurType == Predefine.CT_OTHER)
            {
                String single = String.valueOf(charArray[pCur]);
                if (single.length() != 0)
                    atomSegment.add(new AtomNode(single, nCurType));
                pCur++;
            }
            //如果是字符、数字或者后面跟随了数字的小数点“.”则一直取下去。
            else if (pCur < charArray.length - 1 && ((nCurType == Predefine.CT_SINGLE) || nCurType == Predefine.CT_NUM))
            {
                sb.delete(0, sb.length());
                sb.append(charArray[pCur]);

                boolean reachEnd = true;
                while (pCur < charArray.length - 1)
                {
                    nNextType = charTypeArray[++pCur];

                    if (nNextType == nCurType)
                        sb.append(charArray[pCur]);
                    else
                    {
                        reachEnd = false;
                        break;
                    }
                }
                atomSegment.add(new AtomNode(sb.toString(), nCurType));
                if (reachEnd)
                    pCur++;
            }
            // 对于所有其它情况
            else
            {
                atomSegment.add(new AtomNode(charArray[pCur], nCurType));
                pCur++;
            }
        }

//        logger.trace("原子分词:" + atomSegment);
        return atomSegment;
    }

    /**
     * 将连续的数字节点合并为一个
     *
     * @param linkedArray
     */
    private static void MergeContinueNumIntoOne(List<Vertex> linkedArray)
    {
        if (linkedArray.size() < 2)
            return;

        ListIterator<Vertex> listIterator = linkedArray.listIterator();
        Vertex next = listIterator.next();
        Vertex current = next;
        while (listIterator.hasNext())
        {
            next = listIterator.next();
//            System.out.println("current:" + current + " next:" + next);
            if ((TextUtility.isAllNum(current.realWord) || TextUtility.isAllChineseNum(current.realWord)) && (TextUtility.isAllNum(next.realWord) || TextUtility.isAllChineseNum(next.realWord)))
            {
                /////////// 这部分从逻辑上等同于current.realWord = current.realWord + next.realWord;
                // 但是current指针被几个路径共享，需要备份，不然修改了一处就修改了全局
                current = Vertex.newNumberInstance(current.realWord + next.realWord);
                listIterator.previous();
                listIterator.previous();
                listIterator.set(current);
                listIterator.next();
                listIterator.next();
                /////////// end 这部分
//                System.out.println("before:" + linkedArray);
                listIterator.remove();
//                System.out.println("after:" + linkedArray);
            }
            else
            {
                current = next;
            }
        }

//        logger.trace("数字识别后：" + Graph.parseResult(linkedArray));
    }


    /**
     * 生成一元词网
     *
     * @param sSentence 句子
     * @return 词网
     */
    protected WordNet GenerateWordNet(String sSentence, WordNet wordNetStorage)
    {
        char[] charArray = wordNetStorage.charArray;
        BaseSearcher searcher = CoreDictionary.getSearcher(charArray);
        Map.Entry<String, CoreDictionary.Attribute> entry;
        int p = 0;  // 当前处理到什么位置
        int offset;
        while ((entry = searcher.next()) != null)
        {
            offset = searcher.getOffset();
            // 补足没查到的词
            if (p < offset)
            {
                wordNetStorage.add(p + 1, AtomSegment(wordNetStorage.charArray, p, offset));
            }
            wordNetStorage.add(offset + 1, new Vertex(entry.getKey(), entry.getValue()));
            p = offset + 1;
        }
        // 补足没查到的词
        if (p < sSentence.length())
        {
            wordNetStorage.add(p + 1, AtomSegment(wordNetStorage.charArray, p, sSentence.length()));
        }
        // 用户词典查询
        if (config.useCustomDictionary)
        {
            searcher = CustomDictionary.getSearcher(charArray);
            while ((entry = searcher.next()) != null)
            {
                offset = searcher.getOffset();
                wordNetStorage.add(offset + 1, new Vertex(entry.getKey(), entry.getValue()));
            }
        }
        return wordNetStorage;
    }

    /**
     * 为了索引模式修饰结果
     * @param vertexList
     * @param wordNetAll
     */
    protected static List<Term> decorateResultForIndexMode(List<Vertex> vertexList, WordNet wordNetAll)
    {
        List<Term> termList = new LinkedList<>();
        int line = 1;
        ListIterator<Vertex> listIterator = vertexList.listIterator();
        listIterator.next();
        int length = vertexList.size() - 2;
        for (int i = 0; i < length; ++i)
        {
            Vertex vertex = listIterator.next();
            Term termMain = convert(vertex);
            termList.add(termMain);
            termMain.offset = line - 1;
            if (vertex.realWord.length() > 2)
            {
                // 过长词所在的行
                int currentLine = line;
                while (currentLine < line + vertex.realWord.length())
                {
                    List<Vertex> vertexListCurrentLine = wordNetAll.get(currentLine);    // 这一行的词
                    for (Vertex smallVertex : vertexListCurrentLine) // 这一行的短词
                    {
                        if (smallVertex.realWord.length() > 1 && smallVertex != vertex)
                        {
                            listIterator.add(smallVertex);
                            Term termSub = convert(smallVertex);
                            termSub.offset = currentLine - 1;
                            termList.add(termSub);
                        }
                    }
                    ++currentLine;
                }
            }
            line += vertex.realWord.length();
        }

        return termList;
    }

    private static Term convert(Vertex vertex)
    {
        return new Term(vertex.realWord, vertex.guessNature());
    }
}
