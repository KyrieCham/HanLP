/*
 * <summary></summary>
 * <author>He Han</author>
 * <email>hankcs.cn@gmail.com</email>
 * <create-date>2014/11/12 16:47</create-date>
 *
 * <copyright file="TranslatedPersonRecognition.java" company="上海林原信息科技有限公司">
 * Copyright (c) 2003-2014, 上海林原信息科技有限公司. All Right Reserved, http://www.linrunsoft.com/
 * This source is subject to the LinrunSpace License. Please contact 上海林原信息科技有限公司 to get more information.
 * </copyright>
 */
package com.hankcs.hanlp.recognition.nr;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.corpus.tag.Nature;
import com.hankcs.hanlp.dictionary.nr.TranslatedPersonDictionary;
import com.hankcs.hanlp.seg.common.Vertex;
import com.hankcs.hanlp.seg.common.WordNet;

import java.util.List;
import java.util.ListIterator;

/**
 * 音译人名识别
 * @author hankcs
 */
public class TranslatedPersonRecognition
{
    static StringBuilder sbName = new StringBuilder();
    static int appendTimes = 0;
    /**
     * 执行识别
     * @param segResult 粗分结果
     * @param wordNetOptimum 粗分结果对应的词图
     * @param wordNetAll 全词图
     */
    public static void Recognition(List<Vertex> segResult, WordNet wordNetOptimum, WordNet wordNetAll)
    {
        // nrf触发识别
        ListIterator<Vertex> listIterator = segResult.listIterator();
        listIterator.next();
        int line = 1;
        int activeLine = 1;
        while (listIterator.hasNext())
        {
            Vertex vertex = listIterator.next();
            if (appendTimes > 0)
            {
                if (vertex.guessNature() == Nature.nrf || TranslatedPersonDictionary.containsKey(vertex.realWord))
                {
                    sbName.append(vertex.realWord);
                    ++appendTimes;
                }
                else
                {
                    // 识别结束
                    if (appendTimes > 1)
                    {
                        if (HanLP.Config.DEBUG)
                        {
                            System.out.println("音译人名识别出：" + sbName.toString());
                        }
                        wordNetOptimum.insert(activeLine, Vertex.newTranslatedPersonInstance(sbName.toString(), 1000), wordNetAll);
                    }
                    reset();
                }
            }
            else
            {
                if (vertex.guessNature() == Nature.nrf || TranslatedPersonDictionary.containsKey(vertex.realWord))
                {
                    sbName.append(vertex.realWord);
                    ++appendTimes;
                    activeLine = line;
                }
            }

            line += vertex.realWord.length();
        }
        if (appendTimes > 0) reset();
    }

    private static void reset()
    {
        sbName.setLength(0);
        appendTimes = 0;
    }
}
